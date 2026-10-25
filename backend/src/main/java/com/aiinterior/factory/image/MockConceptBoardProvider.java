package com.aiinterior.factory.image;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Deterministic SVG concept board (palette swatches + material list). It is visibly stamped
 * MOCK and is never presented as an AI-generated interior photograph.
 */
@Component
public class MockConceptBoardProvider implements ImageProvider {

    private static final Map<String, String> COLOURS = Map.ofEntries(
            Map.entry("warm beige", "#d8c3a5"), Map.entry("beige", "#d8c3a5"), Map.entry("ivory", "#f3ede0"),
            Map.entry("walnut", "#5d4030"), Map.entry("oak", "#b88b5a"), Map.entry("sage green", "#9caf88"),
            Map.entry("sage", "#9caf88"), Map.entry("olive green", "#6b7445"), Map.entry("olive", "#6b7445"),
            Map.entry("terracotta", "#c46c4a"), Map.entry("sand", "#d9c49c"), Map.entry("stone grey", "#9a9890"),
            Map.entry("grey", "#9a9890"), Map.entry("oxblood", "#5c1a1b"), Map.entry("brass", "#b5954a"),
            Map.entry("brass accents", "#b5954a"), Map.entry("matte black", "#222222"),
            Map.entry("matte black accents", "#222222"), Map.entry("white", "#f7f7f4"), Map.entry("navy", "#22304a"),
            Map.entry("teal", "#2f6f6f"), Map.entry("cream", "#efe6d2"));

    @Override
    public String name() {
        return "mock-concept-board";
    }

    @Override
    public ImageResult generate(ImageRequest r) {
        int w = 1200;
        int h = 720;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" font-family=\"Inter, sans-serif\">", w, h));
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#f4efe7\"/>");
        sb.append("<text x=\"40\" y=\"70\" font-size=\"34\" font-weight=\"600\" fill=\"#2b2925\">").append(esc(r.title())).append("</text>");
        sb.append("<text x=\"40\" y=\"108\" font-size=\"18\" fill=\"#6b665e\">Concept board - palette and materials</text>");
        List<String> palette = r.palette().isEmpty() ? List.of("ivory", "oak", "stone grey") : r.palette();
        int sw = Math.min(200, (w - 80) / Math.max(1, palette.size()) - 16);
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.get(i);
            String hex = colour(name);
            int x = 40 + i * (sw + 16);
            sb.append(String.format(Locale.ROOT, "<rect x=\"%d\" y=\"150\" width=\"%d\" height=\"220\" rx=\"14\" fill=\"%s\" stroke=\"#00000022\"/>", x, sw, hex));
            sb.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"400\" font-size=\"18\" fill=\"#2b2925\">%s</text>", x, esc(name)));
            sb.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"424\" font-size=\"14\" fill=\"#6b665e\">%s</text>", x, hex));
        }
        int y = 480;
        for (String m : r.materials().stream().limit(6).toList()) {
            sb.append(String.format(Locale.ROOT, "<text x=\"40\" y=\"%d\" font-size=\"18\" fill=\"#2b2925\">• %s</text>", y, esc(m)));
            y += 30;
        }
        sb.append(String.format(Locale.ROOT,
                "<g transform=\"rotate(-12 %d %d)\"><rect x=\"%d\" y=\"%d\" width=\"420\" height=\"86\" rx=\"10\" fill=\"none\" stroke=\"#c0392b\" stroke-width=\"5\"/>"
                        + "<text x=\"%d\" y=\"%d\" font-size=\"44\" font-weight=\"800\" fill=\"#c0392b\" text-anchor=\"middle\">MOCK</text>"
                        + "<text x=\"%d\" y=\"%d\" font-size=\"15\" fill=\"#c0392b\" text-anchor=\"middle\">not an AI render · not to scale</text></g>",
                w - 260, h - 120, w - 470, h - 175, w - 260, h - 128, w - 260, h - 104));
        sb.append("</svg>");
        return new ImageResult(name(), "deterministic-svg-v1", true, "image/svg+xml", sb.toString());
    }

    static String colour(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        if (n.matches("#[0-9a-f]{6}")) {
            return n;
        }
        String c = COLOURS.get(n);
        if (c != null) {
            return c;
        }
        int hash = Math.abs(n.hashCode());
        return String.format(Locale.ROOT, "#%02x%02x%02x", 120 + hash % 100, 110 + (hash / 100) % 100, 100 + (hash / 10000) % 100);
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }
}
