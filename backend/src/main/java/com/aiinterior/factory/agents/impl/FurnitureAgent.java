package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.FurnitureLine;
import com.aiinterior.factory.agents.Payloads.FurnitureRequest;
import com.aiinterior.factory.agents.Payloads.FurnitureSchedule;
import com.aiinterior.factory.agents.Payloads.FurnitureSelection;
import com.aiinterior.factory.agents.Payloads.GeometryResult;
import com.aiinterior.factory.design.FurnitureCatalog;
import com.aiinterior.factory.design.FurnitureSpec;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.geometry.Priority;
import com.aiinterior.factory.geometry.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FurnitureAgent implements Agent {

    public static final String SELECT_FURNITURE = "SELECT_FURNITURE";
    public static final String FURNITURE_SCHEDULE = "FURNITURE_SCHEDULE";

    private final FurnitureCatalog catalog;

    public FurnitureAgent(FurnitureCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public AgentId id() {
        return AgentId.FURNITURE;
    }

    @Override
    public String role() {
        return "Chooses furniture with real sizes and clearances, tracks standard vs custom pieces and checks access around each item.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of();
    }

    @Override
    public Set<String> intents() {
        return Set.of(SELECT_FURNITURE, FURNITURE_SCHEDULE);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) {
        return switch (intent) {
            case SELECT_FURNITURE -> select((FurnitureRequest) payload, ctx);
            case FURNITURE_SCHEDULE -> schedule((GeometryResult) payload);
            default -> throw new IllegalArgumentException(intent);
        };
    }

    private AgentReply select(FurnitureRequest req, AgentContext ctx) {
        List<FurnitureSpec> items = new ArrayList<>(catalog.proposeFor(req.roomType(), req.requests()));
        List<String> notes = new ArrayList<>();
        for (String removal : req.removals()) {
            // The latest revision wins over the original brief; only must-not-change items are protected.
            boolean removed = items.removeIf(f -> f.priority() != Priority.MUST_NOT_CHANGE
                    && f.name().toLowerCase(Locale.ROOT).contains(removal));
            notes.add(removed ? "Removed '" + removal + "' as requested in your revision"
                    : "Revision asked to remove '" + removal + "' but no matching furniture was found");
        }
        long custom = items.stream().filter(f -> f.source().startsWith("CUSTOM")).count();
        if (custom > 0) {
            ctx.warn(custom + " requested item(s) not in the catalogue - sized 600x600x750 mm as an assumption, please give real sizes");
        }
        String list = items.stream().map(f -> f.name() + " [" + f.priority().name().charAt(0) + "]").collect(Collectors.joining(", "));
        return new AgentReply(items.size() + " pieces: " + list, new FurnitureSelection(items, notes));
    }

    private AgentReply schedule(GeometryResult g) {
        List<FurnitureLine> lines = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (GeometrySpec.Item it : g.spec().furniture()) {
            List<ValidationIssue> mine = g.validation().issues().stream().filter(i -> it.id().equals(i.elementId())).toList();
            String clearance = mine.isEmpty() ? "OK" : mine.stream().map(ValidationIssue::code).distinct().collect(Collectors.joining(","));
            mine.forEach(i -> issues.add(i.message()));
            lines.add(new FurnitureLine(it.id(), it.name(), it.category(), it.sizeX(), it.sizeY(), it.height(), it.x(), it.y(),
                    it.againstWall() == null ? "free-standing" : it.againstWall().name(), it.frontClearance(), it.priority().name(),
                    it.source(), clearance, it.source().startsWith("CUSTOM") ? "Size assumed - confirm" : "Typical catalogue size; product to be selected"));
        }
        long ok = lines.stream().filter(l -> l.clearanceStatus().equals("OK")).count();
        return new AgentReply(lines.size() + " items scheduled, " + ok + " with clear access, " + (lines.size() - ok) + " flagged",
                new FurnitureSchedule(lines, issues));
    }
}
