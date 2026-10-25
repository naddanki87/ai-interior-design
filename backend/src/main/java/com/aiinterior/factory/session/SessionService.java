package com.aiinterior.factory.session;

import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentRuntime;
import com.aiinterior.factory.agents.Payloads.ExportRequest;
import com.aiinterior.factory.agents.Payloads.ExportResult;
import com.aiinterior.factory.agents.Payloads.QaReport;
import com.aiinterior.factory.agents.Payloads.VerifyExportsRequest;
import com.aiinterior.factory.agents.impl.QualityAssuranceAgent;
import com.aiinterior.factory.agents.impl.SketchUpExporterAgent;
import com.aiinterior.factory.agents.impl.SupervisorAgent;
import com.aiinterior.factory.common.ApiException;
import com.aiinterior.factory.config.FactoryProperties;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.export.SkpVerification;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** In-memory registry of design sessions; nothing survives a restart by design. */
@Service
public class SessionService {

    private final Map<String, DesignSession> sessions = new ConcurrentHashMap<>();
    private final AgentRuntime runtime;
    private final SessionEvents events;
    private final Clock clock;
    private final int maxSessions;

    public SessionService(AgentRuntime runtime, SessionEvents events, Clock clock, FactoryProperties props) {
        this.runtime = runtime;
        this.events = events;
        this.clock = clock;
        this.maxSessions = Math.max(1, props.agents().maxSessions());
    }

    public DesignSession start(DesignBrief brief) {
        evict();
        DesignSession s = new DesignSession(brief, clock);
        s.onChange(() -> events.changed(s.getId()));
        sessions.put(s.getId(), s);
        runtime.request(s, "USER", AgentId.SUPERVISOR, SupervisorAgent.RUN_DESIGN_WORKFLOW,
                        "Design my " + brief.roomType().label().toLowerCase() + " \"" + brief.roomName() + "\"", brief)
                .whenComplete((r, e) -> {
                    if (e != null && !s.getStatus().finished()) {
                        s.status(SessionStatus.FAILED, e.getMessage());
                    }
                    events.flush(s.getId());
                });
        return s;
    }

    public DesignSession get(String id) {
        DesignSession s = sessions.get(id);
        if (s == null) {
            throw ApiException.notFound("Session", id);
        }
        return s;
    }

    public List<DesignSession> list() {
        return sessions.values().stream().sorted(Comparator.comparing(DesignSession::getCreatedAt).reversed()).toList();
    }

    public DesignSession decide(String id, ApprovalDecision decision) {
        DesignSession s = get(id);
        if (!s.decide(decision)) {
            throw ApiException.conflict("NO_PENDING_APPROVAL", "This session is not waiting for approval");
        }
        return s;
    }

    public DesignSession cancel(String id) {
        DesignSession s = get(id);
        s.requestCancel();
        if (s.getStatus().finished()) {
            return s;
        }
        s.status(SessionStatus.CANCELLED, "Cancelled by you");
        return s;
    }

    /** Re-checks the export folder after the user ran build_model.rb inside SketchUp themselves. */
    public DesignSession verifyExport(String id) {
        DesignSession s = get(id);
        ExportRequest req = s.internal("exportRequest", ExportRequest.class);
        QaReport designQa = s.internal("designQa", QaReport.class);
        if (req == null || designQa == null) {
            throw ApiException.conflict("NOTHING_TO_VERIFY", "No export has been prepared for this session yet");
        }
        runtime.request(s, "USER", AgentId.SKETCHUP_EXPORTER, SketchUpExporterAgent.VERIFY_SKP, "Please verify model.skp now", req)
                .thenCompose(reply -> {
                    ExportResult export = (ExportResult) reply.payload();
                    s.output("export", export);
                    return runtime.request(s, AgentId.SKETCHUP_EXPORTER.name(), AgentId.QUALITY_ASSURANCE,
                            QualityAssuranceAgent.VERIFY_EXPORTS, "Re-run final QA with the verified model",
                            new VerifyExportsRequest(export, designQa, req.designVersion()));
                })
                .whenComplete((AgentReply reply, Throwable e) -> {
                    if (reply != null) {
                        s.output("finalQa", reply.payload());
                        ExportResult export = s.output("export", ExportResult.class);
                        boolean ok = export.skp().status() == SkpVerification.Status.VERIFIED;
                        s.status(ok ? SessionStatus.COMPLETED : SessionStatus.COMPLETED_WITH_WARNINGS, export.message());
                    }
                    events.flush(s.getId());
                });
        return s;
    }

    private void evict() {
        if (sessions.size() < maxSessions) {
            return;
        }
        sessions.values().stream()
                .filter(x -> x.getStatus().finished())
                .min(Comparator.comparing(DesignSession::getCreatedAt))
                .ifPresent(x -> sessions.remove(x.getId()));
        if (sessions.size() >= maxSessions) {
            throw ApiException.conflict("TOO_MANY_SESSIONS", "Too many active sessions - cancel one first");
        }
    }
}
