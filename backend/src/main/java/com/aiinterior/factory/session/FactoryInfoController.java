package com.aiinterior.factory.session;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentRuntime;
import com.aiinterior.factory.ai.AiGateway;
import com.aiinterior.factory.config.FactoryProperties;
import com.aiinterior.factory.domain.Budget;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.domain.DimensionSource;
import com.aiinterior.factory.domain.KeepItemInput;
import com.aiinterior.factory.domain.LengthInput;
import com.aiinterior.factory.domain.LengthUnit;
import com.aiinterior.factory.domain.OpeningInput;
import com.aiinterior.factory.domain.OpeningType;
import com.aiinterior.factory.domain.RoomType;
import com.aiinterior.factory.domain.WallSide;
import com.aiinterior.factory.export.ExportWorkspace;
import com.aiinterior.factory.export.SketchUpLauncher;
import com.aiinterior.factory.image.ImageProvider;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FactoryInfoController {

    public record AgentInfo(String id, String name, String role, List<String> tools, List<String> intents) {
    }

    private final AgentRuntime runtime;
    private final AiGateway ai;
    private final ImageProvider images;
    private final SketchUpLauncher sketchup;
    private final ExportWorkspace workspace;
    private final FactoryProperties props;

    public FactoryInfoController(AgentRuntime runtime, AiGateway ai, ImageProvider images, SketchUpLauncher sketchup,
                                 ExportWorkspace workspace, FactoryProperties props) {
        this.runtime = runtime;
        this.ai = ai;
        this.images = images;
        this.sketchup = sketchup;
        this.workspace = workspace;
        this.props = props;
    }

    @GetMapping("/agents")
    public List<AgentInfo> agents() {
        return runtime.agents().values().stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .map((Agent a) -> new AgentInfo(a.id().name(), a.id().displayName(), a.role(),
                        a.tools().stream().map(Enum::name).sorted().toList(), a.intents().stream().sorted().toList()))
                .toList();
    }

    /** Effective configuration from application.properties. Secrets are reported only as configured/not configured. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aiProvider", ai.providerName());
        m.put("aiModel", ai.modelName());
        m.put("aiConfigurationError", ai.configurationError());
        m.put("huggingFaceBaseUrl", props.huggingface().baseUrl());
        m.put("huggingFaceTokenConfigured", props.huggingface().tokenConfigured());
        m.put("imageProvider", images.name());
        m.put("sketchupConfigured", sketchup.configured());
        m.put("sketchupExecutable", sketchup.describe());
        m.put("exportDir", workspace.root().toString());
        m.put("requireApprovalBeforeExport", props.agents().requireApprovalBeforeExport());
        m.put("stepDelayMs", props.agents().stepDelay().toMillis());
        m.put("roomTypes", Arrays.stream(RoomType.values()).map(r -> Map.of("id", r.name(), "label", r.label())).toList());
        return m;
    }

    @GetMapping("/brief/example")
    public DesignBrief example() {
        return new DesignBrief("Sharma Residence", "Living room", RoomType.LIVING_ROOM,
                new LengthInput("5200", LengthUnit.MM, DimensionSource.MEASURED),
                new LengthInput("4100", LengthUnit.MM, DimensionSource.MEASURED),
                new LengthInput("3000", LengthUnit.MM, DimensionSource.MEASURED),
                List.of(new OpeningInput(OpeningType.DOOR, WallSide.SOUTH, len("300"), len("1000"), len("2100"), null, "Main door"),
                        new OpeningInput(OpeningType.WINDOW, WallSide.EAST, len("1000"), len("1800"), len("1500"), len("750"), "East window"),
                        new OpeningInput(OpeningType.OPENING, WallSide.WEST, len("2600"), len("1200"), len("2100"), null, "Arch to dining")),
                List.of(new KeepItemInput("Existing AC indoor unit cabinet", len("900"), len("300"), len("600"), len("4300"), len("3800"))),
                List.of("3-seater sofa", "TV unit", "2 armchairs", "Bookshelf"),
                "Warm minimalism", null,
                List.of("Warm beige", "Oak", "Sage green", "Brass accents"),
                List.of("Oak veneer", "Matt vitrified flooring"),
                new Budget(650000.0, "INR"),
                List.of("Seating for 5", "Concealed storage"),
                List.of("Warm cove lighting", "Reading light near sofa"),
                "Keep the AC unit where it is. Must have concealed storage for the router and set-top box. Avoid glossy finishes.");
    }

    private static LengthInput len(String mm) {
        return new LengthInput(mm, LengthUnit.MM, DimensionSource.MEASURED);
    }
}
