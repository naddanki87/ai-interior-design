package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.AlternativeReview;
import com.aiinterior.factory.agents.Payloads.DesignProposal;
import com.aiinterior.factory.agents.Payloads.DesignRequest;
import com.aiinterior.factory.agents.Payloads.FurnitureRequest;
import com.aiinterior.factory.agents.Payloads.FurnitureSelection;
import com.aiinterior.factory.agents.Payloads.LayoutCheckRequest;
import com.aiinterior.factory.agents.Payloads.RequirementsDoc;
import com.aiinterior.factory.agents.Payloads.StructuralProposal;
import com.aiinterior.factory.ai.AiGateway;
import com.aiinterior.factory.ai.AiInsights.DesignNarrative;
import com.aiinterior.factory.ai.AiProviderException;
import com.aiinterior.factory.ai.AiPurpose;
import com.aiinterior.factory.ai.AiRequest;
import com.aiinterior.factory.design.LayoutAlternative;
import com.aiinterior.factory.design.LayoutPlanner;
import com.aiinterior.factory.domain.RoomType;
import com.aiinterior.factory.geometry.GeometryValidationReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class InteriorDesignAgent implements Agent {

    public static final String PROPOSE_DESIGN = "PROPOSE_DESIGN";

    private static final Pattern ADD = Pattern.compile("\\badd\\s+(?:an?\\s+)?([a-z0-9 \\-]+?)(?:[,.;]|\\band\\b|$)");
    private static final Pattern REMOVE = Pattern.compile("\\b(?:remove|without|no)\\s+(?:the\\s+)?([a-z\\-]+)");

    private final LayoutPlanner planner;

    public InteriorDesignAgent(LayoutPlanner planner) {
        this.planner = planner;
    }

    @Override
    public AgentId id() {
        return AgentId.INTERIOR_DESIGN;
    }

    @Override
    public String role() {
        return "Creates the design concept and layout alternatives with the Furniture and Geometry agents; preserves fixed elements and explains trade-offs.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.MESSAGING, AgentTool.AI_TEXT);
    }

    @Override
    public Set<String> intents() {
        return Set.of(PROPOSE_DESIGN);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) throws Exception {
        DesignRequest req = (DesignRequest) payload;
        RequirementsDoc r = req.requirements();
        String notes = req.revisionNotes() == null ? "" : req.revisionNotes().toLowerCase(Locale.ROOT);

        List<String> requests = new ArrayList<>(ctx.session().getBrief().furnitureOrEmpty());
        List<String> removals = new ArrayList<>();
        Matcher add = ADD.matcher(notes);
        while (add.find()) {
            requests.add(add.group(1).trim());
        }
        Matcher rem = REMOVE.matcher(notes);
        while (rem.find()) {
            removals.add(rem.group(1));
        }
        FurnitureSelection furniture = ctx.ask(AgentId.FURNITURE, FurnitureAgent.SELECT_FURNITURE,
                "Which furniture (with sizes and clearances) suits this " + r.roomLabel().toLowerCase(Locale.ROOT) + "?",
                new FurnitureRequest(RoomType.valueOf(r.roomType()), requests, removals, r.style()), FurnitureSelection.class);

        List<LayoutAlternative> alternatives = planner.alternatives(req.room().shell(), furniture.items());
        List<AlternativeReview> reviews = new ArrayList<>();
        for (LayoutAlternative alt : alternatives) {
            GeometryValidationReport report = ctx.ask(AgentId.GEOMETRY, GeometryAgent.CHECK_LAYOUT,
                    "Please check " + alt.name() + " for intersections, clearances and blocked openings",
                    new LayoutCheckRequest(req.room().shell(), alt.furniture()), GeometryValidationReport.class);
            int score = alt.score() - report.errorCount() * 20 - report.warningCount() * 3;
            reviews.add(new AlternativeReview(alt, report, score));
        }

        String provider;
        DesignNarrative narrative;
        List<String> extra = new ArrayList<>(furniture.notes());
        try {
            AiGateway.TypedAiResult<DesignNarrative> res = ctx.ai().generate(new AiRequest(AiPurpose.DESIGN_NARRATIVE,
                    "You are a senior interior designer. Describe a concept in the user's chosen style only. "
                            + "Do not change room dimensions, walls, doors or windows; any structural idea must go in structuralSuggestions.",
                    "Room: " + r.roomLabel() + "\nStyle: " + r.style() + "\nPalette: " + String.join(", ", r.palette())
                            + "\nRequired: " + String.join("; ", r.requiredElements()) + "\nMust not change: " + String.join("; ", r.mustNotChange())
                            + (notes.isBlank() ? "" : "\nRevision request: " + req.revisionNotes()),
                    Map.of("roomType", r.roomLabel(), "style", r.style(), "palette", r.palette())), DesignNarrative.class);
            narrative = res.value();
            provider = res.provider() + "/" + res.model();
        } catch (AiProviderException e) {
            provider = "none";
            narrative = new DesignNarrative(r.style() + " " + r.roomLabel().toLowerCase(Locale.ROOT),
                    "Rule-based concept: " + r.style() + " finishes in " + String.join(", ", r.palette()) + ".",
                    List.of("Feature wall on the longest solid wall"), List.of(), List.of());
            extra.add("AI narrative unavailable (" + e.category() + ") - rule-based concept used");
        }
        List<StructuralProposal> structural = narrative.structuralSuggestions().stream()
                .map(s -> new StructuralProposal(s.description(), s.reason(), "AWAITING_YOUR_DECISION", false)).toList();
        if (!structural.isEmpty()) {
            ctx.warn(structural.size() + " structural suggestion(s) recorded for your decision - NOT applied to the geometry");
        }

        String recommended = reviews.stream().max(Comparator.comparingInt(AlternativeReview::score)).orElseThrow().layout().id();
        if (req.preferredLayoutId() != null && reviews.stream().anyMatch(a -> a.layout().id().equals(req.preferredLayoutId()))) {
            recommended = req.preferredLayoutId();
        } else if (notes.contains("option b") || notes.contains("alternative b")) {
            recommended = "alt-b";
        } else if (notes.contains("option a") || notes.contains("alternative a")) {
            recommended = "alt-a";
        }
        DesignProposal proposal = new DesignProposal(narrative.conceptTitle(), narrative.concept(), narrative.featureWallIdeas(),
                narrative.tradeOffs(), reviews, recommended, structural, provider, extra);
        String rec = recommended;
        AlternativeReview best = reviews.stream().filter(a -> a.layout().id().equals(rec)).findFirst().orElseThrow();
        return new AgentReply(String.format(Locale.ROOT, "%s - %d alternatives, recommending %s (score %d, %d unplaced)",
                narrative.conceptTitle(), reviews.size(), best.layout().name(), best.score(), best.layout().unplaced().size()), proposal);
    }
}
