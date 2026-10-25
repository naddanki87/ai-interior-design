package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.LightingRequest;
import com.aiinterior.factory.lighting.LightingPlanner;
import com.aiinterior.factory.lighting.LightingSchedule;
import com.aiinterior.factory.materials.SurfaceAreas;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LightingAgent implements Agent {

    public static final String LIGHTING_PLAN = "LIGHTING_PLAN";

    private final LightingPlanner planner;

    public LightingAgent(LightingPlanner planner) {
        this.planner = planner;
    }

    @Override
    public AgentId id() {
        return AgentId.LIGHTING;
    }

    @Override
    public String role() {
        return "Plans ambient, task and accent lighting with lux targets and colour temperatures; flags missing electrical and ceiling information.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.MESSAGING);
    }

    @Override
    public Set<String> intents() {
        return Set.of(LIGHTING_PLAN);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) throws Exception {
        LightingRequest r = (LightingRequest) payload;
        SurfaceAreas areas = ctx.ask(AgentId.GEOMETRY, GeometryAgent.SURFACE_AREAS, "What is the floor/ceiling area I need to light?",
                r.spec(), SurfaceAreas.class);
        LightingSchedule s = planner.plan(r.roomType(), r.spec(), r.requirements(), r.style());
        return new AgentReply(String.format(Locale.ROOT, "%d lux target over %.2f m²: %d downlights, %d fixtures total, cove %.1f m; %d item(s) of missing info",
                s.targetLux(), areas.ceilingM2(), s.downlightCount(), s.fixtures().size(), s.coveMetres(), s.missingInformation().size()), s);
    }
}
