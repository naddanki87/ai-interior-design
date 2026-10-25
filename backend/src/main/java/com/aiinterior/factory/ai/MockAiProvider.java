package com.aiinterior.factory.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deterministic, offline provider. Output is clearly marked as coming from the mock. */
public class MockAiProvider implements AiProvider {

    private final ObjectMapper mapper;

    public MockAiProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock-deterministic-v1";
    }

    @Override
    public StructuredAiResult generateStructuredOutput(AiRequest request) {
        Map<String, Object> c = request.context();
        String style = str(c.get("style"), "the chosen style");
        String room = str(c.get("roomType"), "room");
        Object body = switch (request.purpose()) {
            case REQUIREMENTS_INSIGHTS -> requirements(c, style, room);
            case DESIGN_NARRATIVE -> Map.of(
                    "conceptTitle", "[MOCK] " + capital(style) + " " + room.toLowerCase(Locale.ROOT),
                    "concept", "[MOCK] A " + style + " " + room.toLowerCase(Locale.ROOT) + " built around "
                            + str(c.get("palette"), "a neutral palette") + ", with the main piece anchored on the longest solid wall "
                            + "and clear circulation from the door.",
                    "featureWallIdeas", List.of("Fluted panel feature wall behind the main seating or bed",
                            "Textured lime-plaster wash in the lightest palette colour"),
                    "tradeOffs", List.of("More storage reduces open floor area",
                            "Keeping windows unobstructed limits where tall units can go"),
                    "structuralSuggestions", List.of(Map.of(
                            "description", "Consider widening the main door opening by 150 mm",
                            "reason", "[MOCK] Example suggestion - recorded for your decision, never applied automatically")));
            case MATERIAL_ADVICE -> Map.of(
                    "picks", List.of(
                            Map.of("surface", "Floor", "finish", "Matt large-format vitrified tile, warm grey", "rationale", "[MOCK] Durable, easy to clean"),
                            Map.of("surface", "Walls", "finish", "Washable low-sheen emulsion in palette base colour", "rationale", "[MOCK] Neutral backdrop"),
                            Map.of("surface", "Feature wall", "finish", "Oak-veneer fluted panels", "rationale", "[MOCK] Adds warmth for " + style),
                            Map.of("surface", "Furniture", "finish", "Matt laminate with brass/black hardware", "rationale", "[MOCK] Matches palette accents")),
                    "notes", "[MOCK] Brands and models to be selected with your vendor.");
        };
        try {
            return new StructuredAiResult(name(), model(), mapper.writeValueAsString(body), AiUsage.none(), 0);
        } catch (JsonProcessingException e) {
            throw new AiProviderException(AiErrorCategory.INVALID_RESPONSE, e.getMessage(), e);
        }
    }

    private Map<String, Object> requirements(Map<String, Object> c, String style, String room) {
        String brief = str(c.get("instructions"), "");
        List<String> constraints = new ArrayList<>();
        for (String sentence : brief.split("[.\\n;]")) {
            String s = sentence.trim();
            String lower = s.toLowerCase(Locale.ROOT);
            if (!s.isEmpty() && (lower.contains("must") || lower.contains("keep") || lower.contains("no ")
                    || lower.contains("don't") || lower.contains("do not") || lower.contains("avoid") || lower.contains("need"))) {
                constraints.add(s);
            }
        }
        List<String> conflicts = new ArrayList<>();
        String lowerBrief = brief.toLowerCase(Locale.ROOT);
        if (style.toLowerCase(Locale.ROOT).contains("minimal") && lowerBrief.contains("lots of storage")) {
            conflicts.add("Minimalist style vs. request for lots of visible storage - prefer concealed, handle-less storage");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", "[MOCK] " + capital(style) + " " + room.toLowerCase(Locale.ROOT) + " design brief with "
                + constraints.size() + " explicit constraint(s) found in your instructions.");
        out.put("extractedConstraints", constraints);
        out.put("conflicts", conflicts);
        out.put("assumptions", List.of("Wall thickness taken as 115 mm for modelling only (does not change interior dimensions)",
                "Electrical points and ceiling construction unknown until provided"));
        return out;
    }

    private static String str(Object o, String fallback) {
        if (o == null) {
            return fallback;
        }
        String s = o instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList()) : o.toString();
        return s.isBlank() ? fallback : s;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
