package com.aiinterior.factory.workflow;

import static com.aiinterior.factory.workflow.WorkflowSupport.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiinterior.factory.agents.Payloads.ExportResult;
import com.aiinterior.factory.agents.Payloads.QaReport;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.domain.LengthInput;
import com.aiinterior.factory.export.SkpVerification;
import com.aiinterior.factory.session.AgentMessage;
import com.aiinterior.factory.session.AgentTask;
import com.aiinterior.factory.session.ApprovalDecision;
import com.aiinterior.factory.session.DesignSession;
import com.aiinterior.factory.session.FactoryInfoController;
import com.aiinterior.factory.session.MessageType;
import com.aiinterior.factory.session.SessionService;
import com.aiinterior.factory.session.SessionStatus;
import com.aiinterior.factory.session.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "factory.ai.provider=mock",
        "factory.agents.step-delay=0ms",
        "factory.huggingface.token=hf_secret_must_not_leak",
        "factory.sketchup.executable="})
@AutoConfigureMockMvc
class DesignWorkflowIntegrationTest {

    @TempDir
    static Path exportDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("factory.export.output-dir", () -> exportDir.toString());
    }

    @Autowired
    SessionService service;

    @Autowired
    FactoryInfoController info;

    @Autowired
    MockMvc mvc;

    @Test
    void fullRunWithApprovalExportsAndReportsHonestly() throws Exception {
        DesignSession s = service.start(info.example());
        s = await(service, s.getId(), SessionStatus.WAITING_FOR_APPROVAL);

        assertThat(s.getTasks()).filteredOn(t -> t.designVersion() == 1 && !t.id().equals("approval") && !t.id().startsWith("export")
                && !t.id().startsWith("verify")).allMatch(t -> t.status() == TaskStatus.SUCCEEDED);
        // Agents talked to each other, not only to the supervisor.
        List<AgentMessage> peer = s.getMessages().stream().filter(m -> m.type() == MessageType.REQUEST
                && !m.from().equals("SUPERVISOR") && !m.from().equals("USER")).toList();
        assertThat(peer).extracting(AgentMessage::from).contains("INTERIOR_DESIGN", "LIGHTING", "MATERIALS", "QUALITY_ASSURANCE");
        assertThat(peer).extracting(AgentMessage::to).contains("FURNITURE", "GEOMETRY");
        // Every request got exactly one correlated reply.
        for (AgentMessage req : s.getMessages().stream().filter(m -> m.type() == MessageType.REQUEST && !m.to().equals("SUPERVISOR")).toList()) {
            assertThat(s.getMessages()).filteredOn(m -> req.id().equals(m.inReplyTo())).hasSize(1);
        }
        assertThat(s.getPendingApproval()).isNotNull();

        service.decide(s.getId(), new ApprovalDecision(ApprovalDecision.Decision.APPROVE, null, "ok"));
        s = await(service, s.getId(), SessionStatus.COMPLETED_WITH_WARNINGS, SessionStatus.COMPLETED);

        ExportResult export = s.output("export", ExportResult.class);
        assertThat(export.skp().status()).isEqualTo(SkpVerification.Status.NOT_PRODUCED);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED_WITH_WARNINGS);
        Path dir = exportDir.resolve(s.getId()).resolve("v1");
        assertThat(dir.resolve("model.skp")).doesNotExist();
        assertThat(dir.resolve("build_model.rb")).isRegularFile();
        assertThat(Files.readString(dir.resolve("geometry.json"))).contains("\"approvalStatus\" : \"APPROVED\"");
        assertThat(export.files()).allMatch(f -> f.verified());
        QaReport finalQa = s.output("finalQa", QaReport.class);
        assertThat(finalQa.checks()).anyMatch(c -> c.id().equals("SKP-01") && c.status().equals("WARN"));
        assertThat(finalQa.checks()).anyMatch(c -> c.id().equals("STRUCT-01") && c.status().equals("PASS"));

        mvc.perform(get("/api/sessions/" + s.getId() + "/files/build_model.rb")).andExpect(status().isOk());
        mvc.perform(get("/api/sessions/" + s.getId() + "/files/..%2Fsecret")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/sessions/" + s.getId() + "/boq.csv")).andExpect(status().isOk());
    }

    @Test
    void requestChangesCreatesANewVersionAndRejectStops() throws Exception {
        DesignSession s = service.start(info.example());
        await(service, s.getId(), SessionStatus.WAITING_FOR_APPROVAL);
        service.decide(s.getId(), new ApprovalDecision(ApprovalDecision.Decision.REQUEST_CHANGES, "alt-b", "Remove the bookshelf"));
        s = await(service, s.getId(), SessionStatus.WAITING_FOR_APPROVAL);
        assertThat(s.getDesignVersion()).isEqualTo(2);
        assertThat(s.getOutputs().get("selectedLayoutId")).isEqualTo("alt-b");
        assertThat(s.task("export").status()).isEqualTo(TaskStatus.CANCELLED);
        var design = (com.aiinterior.factory.agents.Payloads.DesignProposal) s.getOutputs().get("design");
        assertThat(design.alternative("alt-b").layout().furniture()).noneMatch(f -> f.name().contains("Bookshelf"));

        service.decide(s.getId(), new ApprovalDecision(ApprovalDecision.Decision.REJECT, null, "no"));
        s = await(service, s.getId(), SessionStatus.CANCELLED);
        assertThat(s.getTasks()).filteredOn(t -> t.designVersion() == 2).noneMatch(t -> t.status() == TaskStatus.RUNNING || t.status() == TaskStatus.PENDING);
    }

    @Test
    void missingMeasurementsPauseForInputInsteadOfGuessing() throws Exception {
        DesignBrief ex = info.example();
        DesignBrief brief = new DesignBrief(ex.projectName(), ex.roomName(), ex.roomType(), ex.length(), new LengthInput("", null, null),
                null, ex.openings(), ex.keepItems(), ex.furnitureRequests(), ex.style(), null, ex.palette(), ex.materialPreferences(),
                ex.budget(), ex.functionalRequirements(), ex.lightingRequirements(), ex.instructions());
        DesignSession s = await(service, service.start(brief).getId(), SessionStatus.WAITING_FOR_INPUT);
        AgentTask room = s.task("room");
        assertThat(room.status()).isEqualTo(TaskStatus.WAITING_FOR_INPUT);
        assertThat(room.summary()).contains("Room width", "Ceiling height");
        assertThat(s.getOutputs()).doesNotContainKey("geometry");
        assertThat(s.getMessages()).anyMatch(m -> m.type() == MessageType.QUESTION && m.to().equals("USER"));
    }

    @Test
    void apiValidatesInputAndNeverExposesSecrets() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"projectName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        String config = mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.huggingFaceTokenConfigured").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(config).doesNotContain("hf_secret_must_not_leak");
        mvc.perform(get("/api/agents")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(11));
        mvc.perform(post("/api/sessions/ses_000000000000/approval").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvingWhenNothingIsPendingIsAConflict() throws Exception {
        DesignSession s = await(service, service.start(info.example()).getId(), SessionStatus.WAITING_FOR_APPROVAL);
        service.decide(s.getId(), new ApprovalDecision(ApprovalDecision.Decision.APPROVE, null, null));
        await(service, s.getId(), SessionStatus.COMPLETED_WITH_WARNINGS);
        mvc.perform(post("/api/sessions/" + s.getId() + "/approval").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict());
    }
}
