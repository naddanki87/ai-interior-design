package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.AlternativeReview;
import com.aiinterior.factory.agents.Payloads.DesignProposal;
import com.aiinterior.factory.agents.Payloads.DesignRequest;
import com.aiinterior.factory.agents.Payloads.ExportRequest;
import com.aiinterior.factory.agents.Payloads.ExportResult;
import com.aiinterior.factory.agents.Payloads.FurnitureSchedule;
import com.aiinterior.factory.agents.Payloads.GeometryRequest;
import com.aiinterior.factory.agents.Payloads.GeometryResult;
import com.aiinterior.factory.agents.Payloads.LightingRequest;
import com.aiinterior.factory.agents.Payloads.MaterialsRequest;
import com.aiinterior.factory.agents.Payloads.MaterialsResult;
import com.aiinterior.factory.agents.Payloads.QaReport;
import com.aiinterior.factory.agents.Payloads.QaRequest;
import com.aiinterior.factory.agents.Payloads.RenderRequest;
import com.aiinterior.factory.agents.Payloads.RenderSet;
import com.aiinterior.factory.agents.Payloads.RequirementsDoc;
import com.aiinterior.factory.agents.Payloads.RoomSpec;
import com.aiinterior.factory.agents.Payloads.VerifyExportsRequest;
import com.aiinterior.factory.ai.AiProviderException;
import com.aiinterior.factory.config.FactoryProperties;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.lighting.LightingSchedule;
import com.aiinterior.factory.materials.BoqCsvWriter;
import com.aiinterior.factory.session.AgentTask;
import com.aiinterior.factory.session.ApprovalDecision;
import com.aiinterior.factory.session.ApprovalRequest;
import com.aiinterior.factory.session.DesignSession;
import com.aiinterior.factory.session.MessageType;
import com.aiinterior.factory.session.SessionStatus;
import com.aiinterior.factory.session.TaskStatus;
import com.aiinterior.factory.export.SkpVerification;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Application-controlled orchestration. The Supervisor follows a fixed dependency graph, asks the
 * specialists for their parts, retries transient failures a bounded number of times, and stops at
 * the human approval gate. No language model decides the workflow.
 */
@Component
public class SupervisorAgent implements Agent {

    public static final String RUN_DESIGN_WORKFLOW = "RUN_DESIGN_WORKFLOW";

    private record Step(String base, String title, AgentId agent, List<String> deps) {
    }

    private static final List<Step> INTAKE = List.of(
            new Step("requirements", "Parse requirements", AgentId.REQUIREMENTS, List.of()),
            new Step("room", "Room specification & measurement check", AgentId.ROOM_PLANNING, List.of("requirements")));

    private static final List<Step> DESIGN = List.of(
            new Step("design", "Design concept & layout alternatives", AgentId.INTERIOR_DESIGN, List.of("room")),
            new Step("geometry", "Canonical geometry & validation", AgentId.GEOMETRY, List.of("design")),
            new Step("furniture", "Furniture schedule & clearances", AgentId.FURNITURE, List.of("geometry")),
            new Step("lighting", "Lighting plan", AgentId.LIGHTING, List.of("geometry")),
            new Step("materials", "Materials schedule & BOQ estimate", AgentId.MATERIALS, List.of("geometry", "lighting")),
            new Step("render", "2D plan & concept board", AgentId.RENDERING, List.of("materials")),
            new Step("qa", "Design QA review", AgentId.QUALITY_ASSURANCE, List.of("render", "furniture")),
            new Step("approval", "Your approval", AgentId.SUPERVISOR, List.of("qa")),
            new Step("export", "Export SketchUp model (.skp)", AgentId.SKETCHUP_EXPORTER, List.of("approval")),
            new Step("verify", "Verify exports & final QA", AgentId.QUALITY_ASSURANCE, List.of("export")));

    private final FactoryProperties props;
    private final Clock clock;

    public SupervisorAgent(FactoryProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public AgentId id() {
        return AgentId.SUPERVISOR;
    }

    @Override
    public String role() {
        return "Plans the work, assigns tasks to specialists, tracks status, retries bounded failures and enforces human approval.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.MESSAGING, AgentTool.HUMAN_APPROVAL);
    }

    @Override
    public Set<String> intents() {
        return Set.of(RUN_DESIGN_WORKFLOW);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) {
        DesignSession s = ctx.session();
        DesignBrief brief = (DesignBrief) payload;
        String title = brief.projectName() + " - " + brief.roomName();
        plan(s, INTAKE, 1);
        plan(s, DESIGN, 1);
        ctx.tell("Plan ready: " + (INTAKE.size() + DESIGN.size()) + " tasks across 10 specialist agents. Starting with requirements.");
        s.status(SessionStatus.RUNNING, "Agents are working");
        try {
            RequirementsDoc req = run(ctx, "requirements", AgentId.REQUIREMENTS, RequirementsAgent.PARSE_BRIEF,
                    "Please parse the brief for " + title, brief, RequirementsDoc.class);
            s.output("requirements", req);
            RoomSpec room = run(ctx, "room", AgentId.ROOM_PLANNING, RoomPlanningAgent.BUILD_ROOM_SPEC,
                    "Build the room specification and tell me what's missing", brief, RoomSpec.class);
            s.output("room", room);
            if (room.blocking()) {
                s.updateTask("room", t -> t.with(TaskStatus.WAITING_FOR_INPUT, "Waiting for: " + String.join("; ", room.missing()), null, clock.instant()));
                cancelOpen(s, "Waiting for measurements");
                s.post(MessageType.QUESTION, AgentId.SUPERVISOR.name(), "USER", null,
                        "I can't model the room accurately yet. Please provide: " + String.join("; ", room.missing()), room.missing(), null);
                s.status(SessionStatus.WAITING_FOR_INPUT, "Missing measurements: " + String.join("; ", room.missing()));
                return new AgentReply("Paused - waiting for measurements", null);
            }

            String revision = null;
            String preferredLayout = null;
            for (int round = 0; ; round++) {
                int v = s.getDesignVersion();
                if (round > 0) {
                    plan(s, DESIGN, v);
                }
                DesignProposal design = run(ctx, id("design", v), AgentId.INTERIOR_DESIGN, InteriorDesignAgent.PROPOSE_DESIGN,
                        revision == null ? "Propose a concept and layout alternatives" : "Revise the design: " + revision,
                        new DesignRequest(req, room, revision, preferredLayout, v), DesignProposal.class);
                s.output("design", design);
                String layoutId = design.recommendedId();
                s.output("selectedLayoutId", layoutId);
                AlternativeReview chosen = design.alternative(layoutId);

                GeometryResult geo = run(ctx, id("geometry", v), AgentId.GEOMETRY, GeometryAgent.BUILD_GEOMETRY,
                        "Generate canonical geometry for " + chosen.layout().name(),
                        new GeometryRequest(room.shell(), chosen.layout(), s.getId(), v, title), GeometryResult.class);
                s.output("geometry", geo);
                if (!geo.validation().valid()) {
                    String errs = String.join("; ", geo.validation().issues().stream()
                            .filter(i -> i.severity().name().equals("ERROR")).map(i -> i.message()).toList());
                    s.updateTask(id("geometry", v), t -> t.with(TaskStatus.WAITING_FOR_INPUT, "Fix: " + errs, null, clock.instant()));
                    cancelOpen(s, "Geometry has errors that need your input");
                    s.post(MessageType.QUESTION, AgentId.SUPERVISOR.name(), "USER", null,
                            "The room geometry is invalid - please correct these inputs: " + errs, null, null);
                    s.status(SessionStatus.WAITING_FOR_INPUT, "Geometry errors: " + errs);
                    return new AgentReply("Paused - geometry needs corrected inputs", null);
                }

                CompletableFuture<FurnitureSchedule> furnitureF = async(() -> run(ctx, id("furniture", v), AgentId.FURNITURE,
                        FurnitureAgent.FURNITURE_SCHEDULE, "Schedule the furniture and check clearances", geo, FurnitureSchedule.class));
                CompletableFuture<LightingSchedule> lightingF = async(() -> run(ctx, id("lighting", v), AgentId.LIGHTING,
                        LightingAgent.LIGHTING_PLAN, "Plan ambient, task and accent lighting",
                        new LightingRequest(brief.roomType(), geo.spec(), brief.lightingRequirements(), req.style()), LightingSchedule.class));
                LightingSchedule lighting = join(lightingF);
                s.output("lighting", lighting);
                MaterialsResult materials = run(ctx, id("materials", v), AgentId.MATERIALS, MaterialsAgent.MATERIAL_SCHEDULE,
                        "Prepare the material schedule and an indicative BOQ", new MaterialsRequest(req, geo.spec(), lighting), MaterialsResult.class);
                s.output("materials", materials);
                FurnitureSchedule furniture = join(furnitureF);
                s.output("furnitureSchedule", furniture);
                RenderSet render = run(ctx, id("render", v), AgentId.RENDERING, RenderingAgent.RENDER_CONCEPTS,
                        "Produce the plan view and concept board", new RenderRequest(req, geo, materials, title), RenderSet.class);
                s.output("render", render);
                QaReport qa = run(ctx, id("qa", v), AgentId.QUALITY_ASSURANCE, QualityAssuranceAgent.REVIEW_DESIGN,
                        "Review the whole design before it goes to the client",
                        new QaRequest(brief, room, design, geo, furniture, materials, lighting, render, v), QaReport.class);
                s.output("qa", qa);

                String approvalStatus;
                if (props.agents().requireApprovalBeforeExport()) {
                    ApprovalDecision decision = awaitApproval(ctx, v, layoutId, design, qa);
                    if (decision == null || s.isCancelRequested() || decision.decision() == ApprovalDecision.Decision.REJECT) {
                        s.updateTask(id("approval", v), t -> t.with(TaskStatus.CANCELLED, "Rejected", null, clock.instant()));
                        cancelOpen(s, "Design rejected");
                        s.status(SessionStatus.CANCELLED, s.isCancelRequested() ? "Cancelled" : "Design rejected by you");
                        return new AgentReply("Stopped - design rejected", null);
                    }
                    boolean otherLayout = decision.layoutId() != null && !decision.layoutId().isBlank() && !decision.layoutId().equals(layoutId);
                    if (decision.decision() == ApprovalDecision.Decision.REQUEST_CHANGES || otherLayout) {
                        if (round + 1 >= props.agents().maxRevisions()) {
                            s.updateTask(id("approval", v), t -> t.with(TaskStatus.FAILED, null, "Revision limit reached", clock.instant()));
                            cancelOpen(s, "Revision limit reached");
                            s.status(SessionStatus.FAILED, "Revision limit of " + props.agents().maxRevisions() + " reached - start a new session");
                            return new AgentReply("Stopped - revision limit", null);
                        }
                        s.updateTask(id("approval", v), t -> t.with(TaskStatus.SUCCEEDED, "Changes requested: "
                                + (decision.comment() == null ? "switch layout" : decision.comment()), null, clock.instant()));
                        cancelOpen(s, "Superseded by v" + (v + 1));
                        revision = decision.comment();
                        preferredLayout = otherLayout ? decision.layoutId() : null;
                        s.nextDesignVersion();
                        ctx.tell("Starting design version v" + s.getDesignVersion() + (revision == null ? "" : " with your changes: " + revision));
                        s.status(SessionStatus.RUNNING, "Revising design (v" + s.getDesignVersion() + ")");
                        continue;
                    }
                    s.updateTask(id("approval", v), t -> t.with(TaskStatus.SUCCEEDED, "Approved by you"
                            + (decision.comment() == null || decision.comment().isBlank() ? "" : ": " + decision.comment()), null, clock.instant()));
                    approvalStatus = "APPROVED";
                    s.status(SessionStatus.RUNNING, "Approved - exporting");
                } else {
                    s.updateTask(id("approval", v), t -> t.with(TaskStatus.SKIPPED, "Not required (factory.agents.require-approval-before-export=false)", null, clock.instant()));
                    approvalStatus = "APPROVAL_NOT_REQUIRED_BY_CONFIG";
                }

                GeometrySpec approved = withApproval(geo.spec(), approvalStatus);
                s.output("geometry", new GeometryResult(approved, geo.validation(), geo.manifest(), geo.floorPlanSvg()));
                ExportRequest exportReq = new ExportRequest(s.getId(), v, approved, geo.manifest(), geo.floorPlanSvg(),
                        BoqCsvWriter.toCsv(brief.roomName(), materials.boq()));
                s.internal("exportRequest", exportReq);
                ExportResult export = run(ctx, id("export", v), AgentId.SKETCHUP_EXPORTER, SketchUpExporterAgent.EXPORT_SKP,
                        "Build and save the native SketchUp model", exportReq, ExportResult.class);
                s.output("export", export);
                QaReport finalQa = run(ctx, id("verify", v), AgentId.QUALITY_ASSURANCE, QualityAssuranceAgent.VERIFY_EXPORTS,
                        "Verify every exported file", new VerifyExportsRequest(export, qa, v), QaReport.class);
                s.output("finalQa", finalQa);
                s.internal("designQa", qa);
                boolean skpOk = export.skp().status() == SkpVerification.Status.VERIFIED;
                s.status(skpOk ? SessionStatus.COMPLETED : SessionStatus.COMPLETED_WITH_WARNINGS, export.message());
                ctx.tell(skpOk ? "Done - native SketchUp model verified." : "Done - " + export.message());
                return new AgentReply("Workflow finished: " + export.status(), null);
            }
        } catch (Exception e) {
            cancelOpen(s, "Stopped after a failure");
            s.status(s.isCancelRequested() ? SessionStatus.CANCELLED : SessionStatus.FAILED, e.getMessage());
            return new AgentReply("Workflow stopped: " + e.getMessage(), null);
        }
    }

    private ApprovalDecision awaitApproval(AgentContext ctx, int v, String layoutId, DesignProposal design, QaReport qa) throws Exception {
        DesignSession s = ctx.session();
        String task = id("approval", v);
        s.updateTask(task, t -> t.with(TaskStatus.RUNNING, null, null, clock.instant()));
        s.updateTask(task, t -> t.with(TaskStatus.WAITING_FOR_APPROVAL, "Waiting for your decision", null, clock.instant()));
        List<String> warnings = qa.checks().stream().filter(c -> !c.status().equals("PASS")).map(c -> c.status() + " " + c.category() + ": " + c.message()).toList();
        ApprovalRequest request = new ApprovalRequest("Approve design v" + v + " (" + design.alternative(layoutId).layout().name()
                + ") for SketchUp export?", v, layoutId, design.alternatives().stream().map(a -> a.layout().id()).toList(), warnings, clock.instant());
        CompletableFuture<ApprovalDecision> f = s.awaitApproval(request);
        s.post(MessageType.APPROVAL_REQUEST, AgentId.SUPERVISOR.name(), "USER", null, request.question()
                + " QA: " + qa.overall() + ". Structural changes are never applied without you.", request, null);
        s.status(SessionStatus.WAITING_FOR_APPROVAL, request.question());
        s.agentWaiting(AgentId.SUPERVISOR.name(), "you");
        ApprovalDecision d = f.get();
        s.agentResumed(AgentId.SUPERVISOR.name());
        s.post(MessageType.APPROVAL_DECISION, "USER", AgentId.SUPERVISOR.name(), null,
                d.decision() + (d.comment() == null || d.comment().isBlank() ? "" : ": " + d.comment()), d, null);
        return d;
    }

    private <T> T run(AgentContext ctx, String taskId, AgentId agent, String intent, String ask, Object payload, Class<T> type) throws Exception {
        DesignSession s = ctx.session();
        int max = Math.max(1, props.agents().maxTaskAttempts());
        for (int attempt = 1; ; attempt++) {
            if (s.isCancelRequested()) {
                throw new IllegalStateException("Cancelled by you");
            }
            s.updateTask(taskId, t -> t.with(TaskStatus.RUNNING, null, null, clock.instant()));
            try {
                AgentReply reply = ctx.askReply(agent, intent, ask, payload);
                s.updateTask(taskId, t -> t.with(TaskStatus.SUCCEEDED, reply.summary(), null, clock.instant()));
                return type.cast(reply.payload());
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                boolean retryable = e instanceof TimeoutException || (e instanceof AiProviderException ape && ape.category().retryable());
                if (retryable && attempt < max) {
                    int next = attempt + 1;
                    s.updateTask(taskId, t -> t.with(TaskStatus.READY, null, "Attempt failed: " + msg, clock.instant()));
                    ctx.warn("Task '" + taskId + "' failed (" + msg + ") - retrying, attempt " + next + "/" + max);
                    continue;
                }
                s.updateTask(taskId, t -> t.with(TaskStatus.FAILED, null, msg, clock.instant()));
                throw new IllegalStateException(s.task(taskId).title() + " failed: " + msg, e);
            }
        }
    }

    private void plan(DesignSession s, List<Step> steps, int v) {
        for (Step st : steps) {
            List<String> deps = st.deps().stream().map(d -> d.equals("requirements") || d.equals("room") ? d : id(d, v)).toList();
            s.putTask(new AgentTask(id(st.base(), v), st.title() + (v > 1 ? " (v" + v + ")" : ""), st.agent().name(), TaskStatus.PENDING,
                    deps, 0, clock.instant(), null, null, null, null, null, v));
        }
    }

    private void cancelOpen(DesignSession s, String reason) {
        for (AgentTask t : s.getTasks()) {
            if (t.status() == TaskStatus.PENDING || t.status() == TaskStatus.READY) {
                s.updateTask(t.id(), x -> x.with(TaskStatus.CANCELLED, reason, null, clock.instant()));
            }
        }
    }

    private static String id(String base, int v) {
        if (base.equals("requirements") || base.equals("room")) {
            return base;
        }
        return v == 1 ? base : base + "-v" + v;
    }

    private static GeometrySpec withApproval(GeometrySpec g, String status) {
        return new GeometrySpec(g.schemaVersion(), g.sessionId(), g.designVersion(), g.units(), g.coordinateSystem(), g.room(), g.walls(),
                g.openings(), g.fixedObjects(), g.furniture(), g.constraints(), g.measurementSources(), status);
    }

    private interface Call<T> {
        T call() throws Exception;
    }

    private static <T> CompletableFuture<T> async(Call<T> c) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return c.call();
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, r -> Thread.ofVirtual().start(r));
    }

    private static <T> T join(CompletableFuture<T> f) throws Exception {
        try {
            return f.join();
        } catch (java.util.concurrent.CompletionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }
}
