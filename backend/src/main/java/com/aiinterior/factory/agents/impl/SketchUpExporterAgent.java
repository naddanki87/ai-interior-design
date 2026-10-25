package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.ExportFile;
import com.aiinterior.factory.agents.Payloads.ExportRequest;
import com.aiinterior.factory.agents.Payloads.ExportResult;
import com.aiinterior.factory.common.Hashing;
import com.aiinterior.factory.config.FactoryProperties;
import com.aiinterior.factory.export.ExportWorkspace;
import com.aiinterior.factory.export.ObjWriter;
import com.aiinterior.factory.export.SketchUpLauncher;
import com.aiinterior.factory.export.SkpVerification;
import com.aiinterior.factory.export.SkpVerifier;
import com.aiinterior.factory.geometry.GeometryValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Produces the native SketchUp (.skp) model. SketchUp's file format is proprietary, so the
 * model is built by SketchUp itself: this agent writes the build manifest and a Ruby script,
 * launches the configured SketchUp with -RubyStartup, and verifies the saved .skp. It never
 * renames another format to .skp.
 */
@Component
public class SketchUpExporterAgent implements Agent {

    public static final String EXPORT_SKP = "EXPORT_SKP";
    public static final String VERIFY_SKP = "VERIFY_SKP";

    private final ExportWorkspace workspace;
    private final SketchUpLauncher launcher;
    private final SkpVerifier verifier;
    private final ObjectMapper mapper;
    private final FactoryProperties props;

    public SketchUpExporterAgent(ExportWorkspace workspace, SketchUpLauncher launcher, SkpVerifier verifier, ObjectMapper mapper,
                                 FactoryProperties props) {
        this.workspace = workspace;
        this.launcher = launcher;
        this.verifier = verifier;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public AgentId id() {
        return AgentId.SKETCHUP_EXPORTER;
    }

    @Override
    public String role() {
        return "Validates the approved geometry, drives SketchUp's Ruby API to build and save a native .skp, and verifies the file.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.MESSAGING, AgentTool.EXPORT_FILES, AgentTool.SKETCHUP_PROCESS);
    }

    @Override
    public Set<String> intents() {
        return Set.of(EXPORT_SKP, VERIFY_SKP);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) throws Exception {
        ExportRequest req = (ExportRequest) payload;
        return switch (intent) {
            case EXPORT_SKP -> export(req, ctx);
            case VERIFY_SKP -> {
                ExportResult r = result(req, workspace.dir(req.sessionId(), req.designVersion()), false);
                yield new AgentReply(describe(r), r);
            }
            default -> throw new IllegalArgumentException(intent);
        };
    }

    private AgentReply export(ExportRequest req, AgentContext ctx) throws Exception {
        String approval = req.spec().approvalStatus();
        if (props.agents().requireApprovalBeforeExport() && !"APPROVED".equals(approval)) {
            throw new IllegalStateException("Export refused: geometry approval status is " + approval + ", human approval is required");
        }
        GeometryValidationReport check = ctx.ask(AgentId.GEOMETRY, GeometryAgent.VALIDATE,
                "Validate geometry before I build the SketchUp model", req.spec(), GeometryValidationReport.class);
        if (!check.valid()) {
            throw new IllegalStateException("Export refused: geometry has " + check.errorCount() + " error(s)");
        }
        ctx.require(AgentTool.EXPORT_FILES);
        Path dir = workspace.dir(req.sessionId(), req.designVersion());
        Path skp = dir.resolve("model.skp");
        Path result = dir.resolve("sketchup_result.json");
        Files.deleteIfExists(skp);
        Files.deleteIfExists(result);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("geometry.json").toFile(), req.spec());
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("build_manifest.json").toFile(), req.manifest());
        Files.writeString(dir.resolve("model.obj"), ObjWriter.write(req.manifest()), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("floor_plan.svg"), req.floorPlanSvg(), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("boq.csv"), req.boqCsv(), StandardCharsets.UTF_8);

        boolean launch = launcher.configured();
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("schemaVersion", "1.0");
        job.put("sessionId", req.sessionId());
        job.put("designVersion", req.designVersion());
        job.put("manifestPath", slashes(dir.resolve("build_manifest.json")));
        job.put("outputPath", slashes(skp));
        job.put("resultPath", slashes(result));
        job.put("quitWhenDone", launch && props.sketchup().quitWhenDone());
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("job.json").toFile(), job);
        Path script = dir.resolve("build_model.rb");
        Files.writeString(script, builderScript(dir.resolve("job.json")), StandardCharsets.UTF_8);

        if (launch) {
            ctx.require(AgentTool.SKETCHUP_PROCESS);
            ctx.tell("Launching SketchUp to build model.skp (timeout " + props.sketchup().timeout().toMinutes() + " min)");
            SketchUpLauncher.Outcome outcome = launcher.run(script, result, dir);
            if (outcome != SketchUpLauncher.Outcome.RESULT_WRITTEN) {
                ctx.warn("SketchUp run ended with " + outcome);
            }
        } else {
            ctx.warn("SketchUp is not configured (factory.sketchup.executable) - wrote the Ruby build script instead; "
                    + "run it in SketchUp, then press 'Verify .skp'");
        }
        ExportResult r = result(req, dir, launch);
        return new AgentReply(describe(r), r);
    }

    private ExportResult result(ExportRequest req, Path dir, boolean launched) throws IOException {
        SkpVerification skp = verifier.verify(dir.resolve("model.skp"), dir.resolve("sketchup_result.json"), req.manifest());
        List<ExportFile> files = new ArrayList<>();
        files.add(file(dir, "geometry.json", "JSON (geometry schema 1.0)", true, null));
        files.add(file(dir, "build_manifest.json", "JSON (box manifest)", true, null));
        String obj = Files.readString(dir.resolve("model.obj"));
        files.add(file(dir, "model.obj", "Wavefront OBJ (mm) - intermediate, not SketchUp", ObjWriter.verify(obj, req.manifest()),
                "vertex/face counts re-checked against manifest"));
        files.add(file(dir, "floor_plan.svg", "SVG 2D plan", true, null));
        files.add(file(dir, "boq.csv", "CSV indicative BOQ", true, null));
        files.add(file(dir, "build_model.rb", "SketchUp Ruby API script", true, "Run inside SketchUp to create model.skp"));
        if (skp.fileExists()) {
            files.add(new ExportFile("model.skp", "SketchUp native model", skp.sizeBytes(), skp.sha256(),
                    skp.status() == SkpVerification.Status.VERIFIED, String.join("; ", skp.messages())));
        }
        String status = switch (skp.status()) {
            case VERIFIED -> "SKP_VERIFIED";
            case NOT_PRODUCED -> "SKP_NOT_PRODUCED";
            case FAILED -> "SKP_VERIFICATION_FAILED";
        };
        String message = switch (skp.status()) {
            case VERIFIED -> "Native SketchUp model saved and verified";
            case NOT_PRODUCED -> launcher.configured()
                    ? "SketchUp did not produce model.skp - see sketchup_console.log"
                    : "No .skp yet: SketchUp is not configured on this machine. Geometry, OBJ and the Ruby build script are ready.";
            case FAILED -> "model.skp exists but failed verification";
        };
        List<String> manual = List.of(
                "Open SketchUp (2017 or newer) on this computer",
                "Window > Ruby Console, then run: load '" + slashes(dir.resolve("build_model.rb")) + "'",
                "SketchUp builds the room and saves " + slashes(dir.resolve("model.skp")),
                "Back here, press 'Verify .skp' - the exporter checks the file signature and model bounds");
        return new ExportResult(status, message, files, skp, launcher.configured(), launcher.describe(), manual, req.designVersion());
    }

    private String builderScript(Path jobFile) throws IOException {
        String template;
        try (InputStream in = new ClassPathResource("sketchup/aidf_build_model.rb").getInputStream()) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return template + "\nAIDF::Builder.run('" + slashes(jobFile).replace("\\", "\\\\").replace("'", "\\'") + "')\n";
    }

    private static ExportFile file(Path dir, String name, String format, boolean verified, String note) throws IOException {
        Path p = dir.resolve(name);
        byte[] data = Files.readAllBytes(p);
        return new ExportFile(name, format, data.length, Hashing.sha256Hex(data), verified && data.length > 0, note);
    }

    private static String slashes(Path p) {
        return p.toAbsolutePath().toString().replace('\\', '/');
    }

    private static String describe(ExportResult r) {
        return r.status() + " - " + r.message() + " (" + r.files().size() + " files)";
    }
}
