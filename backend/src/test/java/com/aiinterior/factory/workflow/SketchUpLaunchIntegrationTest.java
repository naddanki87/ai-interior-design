package com.aiinterior.factory.workflow;

import static com.aiinterior.factory.workflow.WorkflowSupport.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.aiinterior.factory.agents.Payloads.ExportResult;
import com.aiinterior.factory.export.SkpVerification;
import com.aiinterior.factory.session.ApprovalDecision;
import com.aiinterior.factory.session.DesignSession;
import com.aiinterior.factory.session.FactoryInfoController;
import com.aiinterior.factory.session.SessionService;
import com.aiinterior.factory.session.SessionStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Drives the exporter's launch -> wait -> verify path with tools/sketchup/test/fake_sketchup.sh,
 * a test double that runs the generated build_model.rb against a stub of the SketchUp Ruby API.
 * It proves the pipeline and the Ruby builder logic; it does NOT prove real SketchUp output.
 * Skipped when Ruby is not installed.
 */
@SpringBootTest(properties = {"factory.ai.provider=mock", "factory.agents.step-delay=0ms", "factory.sketchup.timeout=30s"})
class SketchUpLaunchIntegrationTest {

    static final Path FAKE = Path.of("../tools/sketchup/test/fake_sketchup.sh").toAbsolutePath().normalize();

    @TempDir
    static Path exportDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("factory.export.output-dir", () -> exportDir.toString());
        r.add("factory.sketchup.executable", FAKE::toString);
    }

    @BeforeAll
    static void requiresRuby() throws Exception {
        boolean ruby;
        try {
            ruby = new ProcessBuilder("ruby", "-v").start().waitFor() == 0;
        } catch (Exception e) {
            ruby = false;
        }
        assumeTrue(ruby && Files.isExecutable(FAKE), "ruby and fake_sketchup.sh required");
    }

    @Autowired
    SessionService service;

    @Autowired
    FactoryInfoController info;

    @Test
    void launchesBuilderAndVerifiesTheSavedModel() throws Exception {
        DesignSession s = await(service, service.start(info.example()).getId(), SessionStatus.WAITING_FOR_APPROVAL);
        service.decide(s.getId(), new ApprovalDecision(ApprovalDecision.Decision.APPROVE, null, null));
        s = await(service, s.getId(), SessionStatus.COMPLETED, SessionStatus.COMPLETED_WITH_WARNINGS, SessionStatus.FAILED);
        ExportResult export = s.output("export", ExportResult.class);
        assertThat(export.skp().status()).as(export.skp().messages().toString()).isEqualTo(SkpVerification.Status.VERIFIED);
        assertThat(export.skp().boundsMatch()).isTrue();
        assertThat(export.skp().sketchupReport()).containsEntry("status", "SAVED");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }
}
