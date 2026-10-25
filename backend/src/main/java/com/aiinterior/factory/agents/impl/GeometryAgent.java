package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.GeometryRequest;
import com.aiinterior.factory.agents.Payloads.GeometryResult;
import com.aiinterior.factory.agents.Payloads.LayoutCheckRequest;
import com.aiinterior.factory.geometry.BuildManifest;
import com.aiinterior.factory.geometry.BuildManifestFactory;
import com.aiinterior.factory.geometry.FloorPlanSvgRenderer;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.geometry.GeometryValidationReport;
import com.aiinterior.factory.geometry.GeometryValidator;
import com.aiinterior.factory.materials.SurfaceAreaCalculator;
import com.aiinterior.factory.materials.SurfaceAreas;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Owns the canonical geometry. Uses no AI: room dimensions come only from the room
 * specification and are copied, never modified.
 */
@Component
public class GeometryAgent implements Agent {

    public static final String CHECK_LAYOUT = "CHECK_LAYOUT";
    public static final String BUILD_GEOMETRY = "BUILD_GEOMETRY";
    public static final String VALIDATE = "VALIDATE";
    public static final String SURFACE_AREAS = "SURFACE_AREAS";

    private final GeometryValidator validator;
    private final BuildManifestFactory manifests;
    private final FloorPlanSvgRenderer svg;
    private final SurfaceAreaCalculator areas;

    public GeometryAgent(GeometryValidator validator, BuildManifestFactory manifests, FloorPlanSvgRenderer svg, SurfaceAreaCalculator areas) {
        this.validator = validator;
        this.manifests = manifests;
        this.svg = svg;
        this.areas = areas;
    }

    @Override
    public AgentId id() {
        return AgentId.GEOMETRY;
    }

    @Override
    public String role() {
        return "Builds and validates the canonical millimetre geometry (walls, openings, furniture); checks bounds, intersections and clearances.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of();
    }

    @Override
    public Set<String> intents() {
        return Set.of(CHECK_LAYOUT, BUILD_GEOMETRY, VALIDATE, SURFACE_AREAS);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) {
        return switch (intent) {
            case CHECK_LAYOUT -> {
                LayoutCheckRequest r = (LayoutCheckRequest) payload;
                GeometryValidationReport report = validator.validate(withFurniture(r.shell(), r.furniture(), null, 0));
                yield new AgentReply(describe(report), report);
            }
            case BUILD_GEOMETRY -> {
                GeometryRequest r = (GeometryRequest) payload;
                GeometrySpec spec = withFurniture(r.shell(), r.layout().furniture(), r.sessionId(), r.designVersion());
                GeometryValidationReport report = validator.validate(spec);
                BuildManifest manifest = manifests.build(spec);
                String plan = svg.render(spec, manifest, r.title());
                yield new AgentReply(String.format(Locale.ROOT, "Geometry v%d: %d walls, %d openings, %d furniture, %d solids - %s",
                        r.designVersion(), spec.walls().size(), spec.openings().size(), spec.furniture().size(), manifest.boxes().size(),
                        describe(report)), new GeometryResult(spec, report, manifest, plan));
            }
            case VALIDATE -> {
                GeometryValidationReport report = validator.validate((GeometrySpec) payload);
                yield new AgentReply("Independent re-validation: " + describe(report), report);
            }
            case SURFACE_AREAS -> {
                SurfaceAreas a = areas.compute((GeometrySpec) payload);
                yield new AgentReply(String.format(Locale.ROOT, "Floor %.2f m², net walls %.2f m², skirting %.2f m, feature wall %s %.2f m²",
                        a.floorM2(), a.wallNetM2(), a.skirtingM(), a.featureWall(), a.featureWallM2()), a);
            }
            default -> throw new IllegalArgumentException(intent);
        };
    }

    static GeometrySpec withFurniture(GeometrySpec shell, java.util.List<GeometrySpec.Item> furniture, String sessionId, int version) {
        return new GeometrySpec(shell.schemaVersion(), sessionId, version, shell.units(), shell.coordinateSystem(), shell.room(),
                shell.walls(), shell.openings(), shell.fixedObjects(), furniture, shell.constraints(), shell.measurementSources(),
                shell.approvalStatus());
    }

    private static String describe(GeometryValidationReport r) {
        return r.valid() ? (r.warningCount() == 0 ? "valid, no issues" : "valid with " + r.warningCount() + " warning(s)")
                : "INVALID: " + r.errorCount() + " error(s), " + r.warningCount() + " warning(s)";
    }
}
