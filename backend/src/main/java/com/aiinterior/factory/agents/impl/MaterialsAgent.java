package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.MaterialsRequest;
import com.aiinterior.factory.agents.Payloads.MaterialsResult;
import com.aiinterior.factory.ai.AiGateway;
import com.aiinterior.factory.ai.AiInsights.MaterialAdvice;
import com.aiinterior.factory.ai.AiInsights.MaterialPick;
import com.aiinterior.factory.ai.AiProviderException;
import com.aiinterior.factory.ai.AiPurpose;
import com.aiinterior.factory.ai.AiRequest;
import com.aiinterior.factory.materials.BoqCalculator;
import com.aiinterior.factory.materials.BoqEstimate;
import com.aiinterior.factory.materials.MaterialLine;
import com.aiinterior.factory.materials.SurfaceAreas;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MaterialsAgent implements Agent {

    public static final String MATERIAL_SCHEDULE = "MATERIAL_SCHEDULE";

    private final BoqCalculator boq;

    public MaterialsAgent(BoqCalculator boq) {
        this.boq = boq;
    }

    @Override
    public AgentId id() {
        return AgentId.MATERIALS;
    }

    @Override
    public String role() {
        return "Recommends finishes, builds the material schedule from real quantities and produces an indicative, clearly-labelled BOQ estimate.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.MESSAGING, AgentTool.AI_TEXT);
    }

    @Override
    public Set<String> intents() {
        return Set.of(MATERIAL_SCHEDULE);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) throws Exception {
        MaterialsRequest req = (MaterialsRequest) payload;
        SurfaceAreas a = ctx.ask(AgentId.GEOMETRY, GeometryAgent.SURFACE_AREAS, "I need floor, wall and skirting quantities for the schedule",
                req.spec(), SurfaceAreas.class);
        List<String> notes = new ArrayList<>();
        List<MaterialPick> picks;
        String provider;
        try {
            AiGateway.TypedAiResult<MaterialAdvice> res = ctx.ai().generate(new AiRequest(AiPurpose.MATERIAL_ADVICE,
                    "You are a materials specialist. Recommend finishes for Floor, Walls, Feature wall and Furniture. "
                            + "Do not state prices or claim specific product availability.",
                    "Room: " + req.requirements().roomLabel() + "\nStyle: " + req.requirements().style() + "\nPalette: "
                            + String.join(", ", req.requirements().palette()) + "\nUser material preferences: "
                            + String.join("; ", req.requirements().materialPreferences()),
                    Map.of("roomType", req.requirements().roomLabel(), "style", req.requirements().style())), MaterialAdvice.class);
            picks = res.value().picks();
            provider = res.provider() + "/" + res.model();
            if (res.value().notes() != null) {
                notes.add(res.value().notes());
            }
        } catch (AiProviderException e) {
            provider = "none";
            picks = List.of(new MaterialPick("Floor", "Vitrified tile, matt", "rule-based default"),
                    new MaterialPick("Walls", "Washable emulsion", "rule-based default"));
            notes.add("AI material advice unavailable (" + e.category() + ") - defaults used");
        }
        List<MaterialLine> schedule = new ArrayList<>();
        schedule.add(new MaterialLine("Floor", "Flooring", finish(picks, "Floor"), "To be selected", a.floorM2(), "m²", "Floor area from geometry", "Geometry"));
        schedule.add(new MaterialLine("Floor", "Skirting", "Matching tile skirting 100 mm", "To be selected", a.skirtingM(), "rm", "Perimeter minus door widths", "Geometry"));
        schedule.add(new MaterialLine("Walls", "Paint", finish(picks, "Walls"), "To be selected", a.wallNetM2(), "m²", "Net wall area (openings deducted)", "Geometry"));
        schedule.add(new MaterialLine("Feature wall", "Panelling", finish(picks, "Feature wall"), "To be selected", a.featureWallM2(), "m²",
                a.featureWall() + " wall, full height", "Geometry"));
        schedule.add(new MaterialLine("Ceiling", "Paint", "Ceiling white, matt", "To be selected", a.ceilingM2(), "m²", "Ceiling area", "Geometry"));
        schedule.add(new MaterialLine("Furniture", "Carpentry finish", finish(picks, "Furniture"), "To be selected", null, "-",
                "See BOQ per item", "Recommendation"));
        for (String pref : req.requirements().materialPreferences()) {
            schedule.add(new MaterialLine("User preference", pref, pref, "As specified by you", null, "-", "Your brief", "User"));
        }
        BoqEstimate estimate = boq.estimate(req.spec(), a, req.lighting().downlightCount(), req.lighting().coveMetres(), req.requirements().budget());
        return new AgentReply(String.format(Locale.ROOT, "%d schedule lines, %d BOQ lines, indicative total %,.0f %s (%s)",
                schedule.size(), estimate.lines().size(), estimate.grandTotal(), estimate.currency(), estimate.budgetStatus()),
                new MaterialsResult(a, schedule, estimate, provider, notes));
    }

    private static String finish(List<MaterialPick> picks, String surface) {
        return picks.stream().filter(p -> p.surface().equalsIgnoreCase(surface)).map(MaterialPick::finish).findFirst().orElse("To be decided");
    }
}
