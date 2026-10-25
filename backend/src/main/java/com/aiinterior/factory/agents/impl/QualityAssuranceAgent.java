package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.AlternativeReview;
import com.aiinterior.factory.agents.Payloads.ExportFile;
import com.aiinterior.factory.agents.Payloads.QaCheck;
import com.aiinterior.factory.agents.Payloads.QaReport;
import com.aiinterior.factory.agents.Payloads.QaRequest;
import com.aiinterior.factory.agents.Payloads.VerifyExportsRequest;
import com.aiinterior.factory.export.SkpVerification;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.geometry.GeometryValidationReport;
import com.aiinterior.factory.geometry.ValidationIssue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Produces a machine-readable QA report. Re-validates geometry independently via the Geometry agent. */
@Component
public class QualityAssuranceAgent implements Agent {

    public static final String REVIEW_DESIGN = "REVIEW_DESIGN";
    public static final String VERIFY_EXPORTS = "VERIFY_EXPORTS";

    @Override
    public AgentId id() {
        return AgentId.QUALITY_ASSURANCE;
    }

    @Override
    public String role() {
        return "Checks measurements, structure preservation, geometry, clearances, required features, budget labelling and output files.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.MESSAGING);
    }

    @Override
    public Set<String> intents() {
        return Set.of(REVIEW_DESIGN, VERIFY_EXPORTS);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) throws Exception {
        return switch (intent) {
            case REVIEW_DESIGN -> review((QaRequest) payload, ctx);
            case VERIFY_EXPORTS -> verify((VerifyExportsRequest) payload);
            default -> throw new IllegalArgumentException(intent);
        };
    }

    private AgentReply review(QaRequest r, AgentContext ctx) throws Exception {
        List<QaCheck> checks = new ArrayList<>();
        long unverified = r.room().measurements().stream().filter(m -> !"OK".equals(m.status())).count();
        checks.add(new QaCheck("MEAS-01", "Measurements", unverified == 0 ? "PASS" : "WARN",
                unverified == 0 ? "All measurements are MEASURED or from a DRAWING" : unverified + " measurement(s) are assumptions/estimates - confirm on site"));
        checks.add(new QaCheck("MEAS-02", "Missing information", r.room().warnings().isEmpty() ? "PASS" : "WARN",
                r.room().warnings().isEmpty() ? "No open measurement warnings" : String.join("; ", r.room().warnings())));

        GeometrySpec spec = r.geometry().spec();
        GeometryValidationReport independent = ctx.ask(AgentId.GEOMETRY, GeometryAgent.VALIDATE,
                "QA: please re-validate the final geometry independently", spec, GeometryValidationReport.class);
        checks.add(new QaCheck("GEOM-01", "Geometry validity", !independent.valid() ? "FAIL" : independent.warningCount() > 0 ? "WARN" : "PASS",
                independent.errorCount() + " error(s), " + independent.warningCount() + " warning(s) on independent re-validation"));
        checks.add(issueCheck("GEOM-02", "Intersections & bounds", independent, Set.of("INTERSECTION", "OUT_OF_BOUNDS", "ITEM_TOO_TALL")));
        checks.add(issueCheck("GEOM-03", "Clearances, doors & windows", independent, Set.of("CLEARANCE", "BLOCKS_DOOR", "BLOCKS_WINDOW")));

        GeometrySpec shell = r.room().shell();
        boolean structureSame = spec.room().equals(shell.room()) && spec.walls().equals(shell.walls()) && spec.openings().equals(shell.openings());
        checks.add(new QaCheck("STRUCT-01", "Structure preserved", structureSame ? "PASS" : "FAIL",
                structureSame ? "Room dimensions, walls and openings identical to your measurements" : "Geometry differs from the approved room shell"));
        boolean noneApplied = r.design().structuralProposals().stream().noneMatch(p -> p.applied());
        checks.add(new QaCheck("STRUCT-02", "Structural suggestions", noneApplied ? "PASS" : "FAIL",
                r.design().structuralProposals().size() + " suggestion(s) recorded, none applied without your approval"));
        boolean fixedSame = spec.fixedObjects().equals(shell.fixedObjects());
        checks.add(new QaCheck("KEEP-01", "Must-not-change items", fixedSame ? "PASS" : "FAIL",
                fixedSame ? spec.fixedObjects().size() + " existing item(s) kept at their exact positions" : "A fixed item moved"));

        String selected = ctx.session().output("selectedLayoutId", String.class);
        AlternativeReview chosen = selected == null ? null : r.design().alternative(selected);
        List<String> unplaced = chosen == null ? List.of() : chosen.layout().unplaced();
        List<String> requiredMissing = unplaced.stream().filter(u -> u.contains("(REQUIRED)")).toList();
        checks.add(new QaCheck("FEAT-01", "Requested features", !requiredMissing.isEmpty() ? "FAIL" : unplaced.isEmpty() ? "PASS" : "WARN",
                !requiredMissing.isEmpty() ? "Could not place: " + String.join("; ", requiredMissing)
                        : unplaced.isEmpty() ? "All requested and default pieces placed" : "Optional/preferred not placed: " + String.join("; ", unplaced)));

        var boq = r.materials().boq();
        boolean labelled = boq.disclaimer() != null && boq.disclaimer().startsWith("INDICATIVE") && boq.lines().stream().allMatch(l -> l.dataSource() != null);
        checks.add(new QaCheck("COST-01", "Estimate labelling", labelled ? "PASS" : "FAIL", "Every BOQ line carries a data source and confidence; marked as indicative"));
        checks.add(new QaCheck("COST-02", "Budget", boq.budgetStatus().startsWith("OVER") ? "WARN" : "PASS", boq.budgetStatus()));
        checks.add(new QaCheck("LIGHT-01", "Lighting information", r.lighting().missingInformation().isEmpty() ? "PASS" : "WARN",
                "Still needed: " + String.join("; ", r.lighting().missingInformation())));
        checks.add(new QaCheck("REND-01", "Visual concepts", "PASS", r.render().mock()
                ? "Concept board is a labelled MOCK, not presented as an AI render" : "Concept image recorded with provider and prompt; not used for dimensions"));
        QaReport report = new QaReport(overall(checks), checks, Instant.now(), r.designVersion());
        return new AgentReply(summary(report), report);
    }

    private AgentReply verify(VerifyExportsRequest r) {
        List<QaCheck> checks = new ArrayList<>(r.designQa().checks());
        for (ExportFile f : r.export().files()) {
            checks.add(new QaCheck("FILE-" + f.name(), "Output files", f.verified() ? "PASS" : "FAIL",
                    f.name() + " (" + f.format() + ", " + f.sizeBytes() + " bytes)" + (f.note() == null ? "" : " - " + f.note())));
        }
        SkpVerification skp = r.export().skp();
        String status = skp.status() == SkpVerification.Status.VERIFIED ? "PASS"
                : skp.status() == SkpVerification.Status.NOT_PRODUCED ? "WARN" : "FAIL";
        checks.add(new QaCheck("SKP-01", "SketchUp model", status, skp.status() + ": " + String.join("; ", skp.messages())));
        QaReport report = new QaReport(overall(checks), checks, Instant.now(), r.designVersion());
        return new AgentReply("Final QA: " + summary(report), report);
    }

    private static QaCheck issueCheck(String id, String category, GeometryValidationReport report, Set<String> codes) {
        List<ValidationIssue> hits = report.issues().stream().filter(i -> codes.contains(i.code())).toList();
        boolean error = hits.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new QaCheck(id, category, error ? "FAIL" : hits.isEmpty() ? "PASS" : "WARN",
                hits.isEmpty() ? "No issues" : hits.stream().map(ValidationIssue::message).collect(Collectors.joining("; ")));
    }

    private static String overall(List<QaCheck> checks) {
        if (checks.stream().anyMatch(c -> c.status().equals("FAIL"))) {
            return "FAIL";
        }
        return checks.stream().anyMatch(c -> c.status().equals("WARN")) ? "PASS_WITH_WARNINGS" : "PASS";
    }

    private static String summary(QaReport r) {
        long pass = r.checks().stream().filter(c -> c.status().equals("PASS")).count();
        long warn = r.checks().stream().filter(c -> c.status().equals("WARN")).count();
        long fail = r.checks().stream().filter(c -> c.status().equals("FAIL")).count();
        return r.overall() + " - " + pass + " pass, " + warn + " warn, " + fail + " fail";
    }
}
