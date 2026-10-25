package com.aiinterior.factory.geometry;

import com.aiinterior.factory.domain.OpeningType;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** 2D plan drawn directly from geometry (dimensioned, to scale). */
@Component
public class FloorPlanSvgRenderer {

    public String render(GeometrySpec spec, BuildManifest manifest, String title) {
        double l = spec.room().length();
        double w = spec.room().width();
        double pad = 700;
        double vbW = l + pad * 2;
        double vbH = w + pad * 2;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%.0f %.0f %.0f %.0f\" font-family=\"Inter, sans-serif\">",
                -pad, -pad, vbW, vbH));
        // Flip Y so +Y (north) is up on screen.
        sb.append(String.format(Locale.ROOT, "<g transform=\"translate(0 %.0f) scale(1 -1)\">", w));
        sb.append(String.format(Locale.ROOT, "<rect x=\"0\" y=\"0\" width=\"%.0f\" height=\"%.0f\" fill=\"#f6f1e9\"/>", l, w));
        for (BuildManifest.Box b : manifest.boxes()) {
            if (b.id().equals("floor")) {
                continue;
            }
            String fill = switch (b.material()) {
                case "Wall" -> "#3d3a36";
                case "Fixed" -> "#8b95a3";
                case "Rug" -> "#d9a58f";
                default -> "#c8a77e";
            };
            if (b.material().equals("Wall") && b.min()[2] > 0) {
                continue; // lintel above an opening - keep the gap visible in plan
            }
            if (b.material().equals("Wall") && b.max()[2] < spec.room().height() - 1) {
                fill = "#a9a39a"; // sill wall under a window
            }
            sb.append(String.format(Locale.ROOT,
                    "<rect x=\"%.0f\" y=\"%.0f\" width=\"%.0f\" height=\"%.0f\" fill=\"%s\" fill-opacity=\"%s\" stroke=\"#2b2925\" stroke-width=\"%s\"/>",
                    b.min()[0], b.min()[1], b.max()[0] - b.min()[0], b.max()[1] - b.min()[1], fill,
                    b.material().equals("Wall") ? "1" : "0.85", b.material().equals("Wall") ? "0" : "8"));
        }
        for (KeepOutZones.Zone z : KeepOutZones.of(spec)) {
            if (z.type() != OpeningType.WINDOW) {
                Rect r = z.rect();
                sb.append(String.format(Locale.ROOT,
                        "<rect x=\"%.0f\" y=\"%.0f\" width=\"%.0f\" height=\"%.0f\" fill=\"none\" stroke=\"#b5651d\" stroke-dasharray=\"60 40\" stroke-width=\"10\"/>",
                        r.x(), r.y(), r.w(), r.d()));
            }
        }
        sb.append("</g>");
        for (GeometrySpec.Item it : spec.furniture()) {
            double cx = it.x() + it.sizeX() / 2;
            // Rugs sit under other furniture: label them along their lower edge so labels don't collide.
            double cy = "RUG".equals(it.category()) ? w - it.y() - 90 : w - (it.y() + it.sizeY() / 2);
            double fs = Math.max(70, Math.min(140, Math.min(it.sizeX(), it.sizeY()) / 4));
            sb.append(String.format(Locale.ROOT,
                    "<text x=\"%.0f\" y=\"%.0f\" font-size=\"%.0f\" text-anchor=\"middle\" dominant-baseline=\"middle\" fill=\"#2b2925\">%s</text>",
                    cx, cy, fs, HtmlUtils.htmlEscape(it.name())));
        }
        sb.append(String.format(Locale.ROOT,
                "<text x=\"%.0f\" y=\"%.0f\" font-size=\"150\" text-anchor=\"middle\" fill=\"#5b5750\">%.0f mm</text>", l / 2, w + 420, l));
        sb.append(String.format(Locale.ROOT,
                "<text x=\"%.0f\" y=\"%.0f\" font-size=\"150\" text-anchor=\"middle\" fill=\"#5b5750\" transform=\"rotate(-90 %.0f %.0f)\">%.0f mm</text>",
                -420.0, w / 2, -420.0, w / 2, w));
        sb.append(String.format(Locale.ROOT,
                "<text x=\"%.0f\" y=\"%.0f\" font-size=\"160\" fill=\"#2b2925\" font-weight=\"600\">%s</text>",
                -pad + 80, -pad + 220, HtmlUtils.htmlEscape(title)));
        sb.append(String.format(Locale.ROOT, "<text x=\"%.0f\" y=\"%.0f\" font-size=\"110\" fill=\"#5b5750\">N ↑</text>", l + 150, -pad + 220));
        sb.append("</svg>");
        return sb.toString();
    }
}
