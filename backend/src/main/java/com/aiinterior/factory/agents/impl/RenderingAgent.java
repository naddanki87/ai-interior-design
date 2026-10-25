package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.RenderRequest;
import com.aiinterior.factory.agents.Payloads.RenderSet;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.image.ImageProvider;
import com.aiinterior.factory.materials.MaterialLine;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RenderingAgent implements Agent {

    public static final String RENDER_CONCEPTS = "RENDER_CONCEPTS";
    public static final String DISCLAIMER = "Concept visuals only - they are not dimensionally accurate. The geometry and the SketchUp model are the source of truth.";

    private final ImageProvider images;

    public RenderingAgent(ImageProvider images) {
        this.images = images;
    }

    @Override
    public AgentId id() {
        return AgentId.RENDERING;
    }

    @Override
    public String role() {
        return "Plans cameras, writes the image prompt, produces the 2D plan view and concept board; never treats an image as proof of dimensions.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of(AgentTool.IMAGE_GENERATION);
    }

    @Override
    public Set<String> intents() {
        return Set.of(RENDER_CONCEPTS);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) {
        ctx.require(AgentTool.IMAGE_GENERATION);
        RenderRequest r = (RenderRequest) payload;
        GeometrySpec g = r.geometry().spec();
        String furniture = g.furniture().stream().map(GeometrySpec.Item::name).collect(Collectors.joining(", "));
        String finishes = r.materials().schedule().stream().filter(m -> !m.finish().equals("To be decided"))
                .map(m -> m.surface() + ": " + m.finish()).distinct().limit(6).collect(Collectors.joining("; "));
        String prompt = String.format(Locale.ROOT, "Photorealistic interior, %s %s, palette %s, %s, furniture: %s, soft natural light, eye-level 1500 mm",
                r.requirements().style(), r.requirements().roomLabel().toLowerCase(Locale.ROOT), String.join(", ", r.requirements().palette()),
                finishes, furniture);
        String negative = "distorted proportions, extra doors, extra windows, text, watermark, people";
        List<String> cameras = List.of(
                String.format(Locale.ROOT, "CAM-1 from SW corner (300, 300, 1500) looking to NE, 24 mm"),
                String.format(Locale.ROOT, "CAM-2 from NE corner (%.0f, %.0f, 1500) looking to SW, 24 mm", g.room().length() - 300, g.room().width() - 300));
        Instant requested = Instant.now();
        List<String> materials = r.materials().schedule().stream().map(MaterialLine::finish).filter(f -> !f.equals("To be decided")).distinct().toList();
        ImageProvider.ImageResult img = images.generate(new ImageProvider.ImageRequest(r.title(), prompt, negative,
                r.requirements().palette(), materials, 1200, 720));
        RenderSet set = new RenderSet(r.geometry().floorPlanSvg(), img.content(), img.provider(), img.model(), img.mock(), prompt, negative,
                cameras, DISCLAIMER, requested, Instant.now());
        return new AgentReply("2D plan + concept board via " + img.provider() + (img.mock() ? " (MOCK - not an AI render)" : ""), set);
    }
}
