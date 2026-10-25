package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.Fact;
import com.aiinterior.factory.agents.Payloads.RequirementsDoc;
import com.aiinterior.factory.ai.AiGateway;
import com.aiinterior.factory.ai.AiInsights.RequirementsInsights;
import com.aiinterior.factory.ai.AiProviderException;
import com.aiinterior.factory.ai.AiPurpose;
import com.aiinterior.factory.ai.AiRequest;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.domain.Dimension;
import com.aiinterior.factory.domain.KeepItemInput;
import com.aiinterior.factory.domain.LengthInput;
import com.aiinterior.factory.units.LengthParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RequirementsAgent implements Agent {

    public static final String PARSE_BRIEF = "PARSE_BRIEF";

    @Override
    public AgentId id() {
        return AgentId.REQUIREMENTS;
    }

    @Override
    public String role() {
        return "Parses the brief, separates facts from assumptions, lists required / preferred / optional / must-not-change elements and flags conflicts.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.AI_TEXT);
    }

    @Override
    public Set<String> intents() {
        return Set.of(PARSE_BRIEF);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) {
        DesignBrief b = (DesignBrief) payload;
        String style = b.customStyle() != null && !b.customStyle().isBlank() ? b.customStyle()
                : b.style() == null || b.style().isBlank() ? "unspecified style" : b.style();

        List<String> required = new ArrayList<>();
        b.furnitureOrEmpty().stream().filter(s -> !s.isBlank()).forEach(s -> required.add("Furniture: " + s.trim()));
        list(b.functionalRequirements()).forEach(s -> required.add("Function: " + s));
        List<String> preferred = new ArrayList<>();
        preferred.add("Style: " + style);
        if (!b.paletteOrEmpty().isEmpty()) {
            preferred.add("Palette: " + String.join(", ", b.paletteOrEmpty()));
        }
        list(b.materialPreferences()).forEach(s -> preferred.add("Material: " + s));
        list(b.lightingRequirements()).forEach(s -> preferred.add("Lighting: " + s));
        List<String> optional = List.of("Feature wall treatment", "Decor accessories and soft furnishings", "Accent lighting");
        List<String> mustNotChange = new ArrayList<>(List.of(
                "Room dimensions, walls, doors, windows and openings (structural - change only with your explicit approval)"));
        for (KeepItemInput k : b.keepItemsOrEmpty()) {
            mustNotChange.add("Existing item stays in place: " + k.name());
        }

        List<Fact> facts = new ArrayList<>();
        Map<String, Dimension> dims = new LinkedHashMap<>();
        dims.put("Length", dim(b.length()));
        dims.put("Width", dim(b.width()));
        dims.put("Ceiling height", dim(b.height()));
        dims.forEach((k, d) -> facts.add(d.known()
                ? new Fact(k + " = " + d.mm().intValue() + " mm", d.source().name())
                : new Fact(k + " not provided", "UNKNOWN")));
        facts.add(new Fact(b.openingsOrEmpty().size() + " door/window opening(s) described", "USER_STATED"));
        if (b.budget() != null && b.budget().amount() != null) {
            facts.add(new Fact("Budget " + b.budget().amount().longValue() + " " + (b.budget().currency() == null ? "INR" : b.budget().currency()), "USER_STATED"));
        }

        List<String> conflicts = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        Dimension l = dims.get("Length");
        Dimension w = dims.get("Width");
        if (l.known() && w.known() && b.budget() != null && b.budget().amount() != null && b.budget().amount() > 0) {
            double area = l.value() * w.value() / 1e6;
            double perM2 = b.budget().amount() / area;
            if (style.toLowerCase(Locale.ROOT).contains("luxury") && perM2 < 15000) {
                conflicts.add(String.format(Locale.ROOT, "Budget of about %.0f per m² is tight for a %s finish level", perM2, style));
            }
        }
        if (b.furnitureOrEmpty().size() > 8 && style.toLowerCase(Locale.ROOT).contains("minimal")) {
            conflicts.add("Many furniture pieces requested for a minimalist style");
        }

        String summary = b.roomType().label() + " in " + style + ".";
        List<String> constraints = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String provider;
        try {
            AiGateway.TypedAiResult<RequirementsInsights> r = ctx.ai().generate(new AiRequest(AiPurpose.REQUIREMENTS_INSIGHTS,
                    "You are an interior-design requirements analyst. Extract explicit constraints and conflicts from the brief. "
                            + "Never invent measurements.",
                    "Room: " + b.roomType().label() + "\nStyle: " + style + "\nPalette: " + String.join(", ", b.paletteOrEmpty())
                            + "\nFunctional needs: " + String.join("; ", list(b.functionalRequirements()))
                            + "\nInstructions: " + (b.instructions() == null ? "" : b.instructions()),
                    Map.of("roomType", b.roomType().label(), "style", style, "palette", b.paletteOrEmpty(),
                            "instructions", b.instructions() == null ? "" : b.instructions())), RequirementsInsights.class);
            summary = r.value().summary();
            constraints.addAll(r.value().extractedConstraints());
            conflicts.addAll(r.value().conflicts().stream().map(c -> c + " (AI-identified)").toList());
            assumptions.addAll(r.value().assumptions().stream().map(a -> a + " (AI-suggested assumption)").toList());
            provider = r.provider() + "/" + r.model();
        } catch (AiProviderException e) {
            provider = "none";
            notes.add("AI insights unavailable (" + e.category() + "): continuing with rule-based parsing only");
            ctx.warn("AI provider unavailable (" + e.category() + ") - using rule-based requirements only");
        }
        RequirementsDoc doc = new RequirementsDoc(b.roomType().name(), b.roomType().label(), style, b.paletteOrEmpty(),
                list(b.materialPreferences()), b.budget(), required, preferred, optional, mustNotChange,
                list(b.functionalRequirements()), list(b.lightingRequirements()), constraints, facts, assumptions, conflicts,
                summary, provider, notes);
        return new AgentReply(String.format(Locale.ROOT, "%d required, %d preferred, %d must-not-change, %d conflict(s)",
                required.size(), preferred.size(), mustNotChange.size(), conflicts.size()), doc);
    }

    private static Dimension dim(LengthInput in) {
        try {
            return LengthParser.toDimension(in);
        } catch (IllegalArgumentException e) {
            return Dimension.unknown();
        }
    }

    private static List<String> list(List<String> l) {
        return l == null ? List.of() : l.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
    }
}
