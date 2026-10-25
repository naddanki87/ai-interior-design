package com.aiinterior.factory.lighting;

import com.aiinterior.factory.domain.RoomType;
import com.aiinterior.factory.geometry.GeometrySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Lumen method: lumens = lux x area / (utilisation 0.6 x maintenance 0.8). */
@Component
public class LightingPlanner {

    private static final int DOWNLIGHT_LUMENS = 900;

    public LightingSchedule plan(RoomType type, GeometrySpec g, List<String> userRequirements, String style) {
        double l = g.room().length();
        double w = g.room().width();
        double areaM2 = l * w / 1e6;
        int lux = switch (type) {
            case KITCHEN, STUDY, HOME_OFFICE -> 400;
            case BATHROOM, UTILITY_BALCONY, DINING_ROOM, LIVING_ROOM -> 250;
            case POOJA_ROOM, FOYER -> 200;
            case BALCONY -> 100;
            default -> 150;
        };
        String cct = switch (type) {
            case KITCHEN, STUDY, HOME_OFFICE, UTILITY_BALCONY, BATHROOM -> "4000K";
            default -> "3000K";
        };
        int lumens = (int) Math.ceil(lux * areaM2 / (0.6 * 0.8));
        int count = Math.max(1, (int) Math.ceil((double) lumens / DOWNLIGHT_LUMENS));
        int cols = Math.max(1, (int) Math.round(Math.sqrt(count * l / w)));
        int rows = (int) Math.ceil((double) count / cols);
        List<LightingSchedule.Fixture> fixtures = new ArrayList<>();
        int n = 0;
        for (int r = 0; r < rows && n < count; r++) {
            for (int c = 0; c < cols && n < count; c++) {
                n++;
                fixtures.add(new LightingSchedule.Fixture("DL-" + n, "AMBIENT", "Recessed LED downlight 12 W", cct, DOWNLIGHT_LUMENS,
                        round(l * (c + 0.5) / cols), round(w * (r + 0.5) / rows), "Ceiling", null));
            }
        }
        List<String> missing = new ArrayList<>(List.of(
                "Existing electrical and switch point locations",
                "Ceiling construction (RCC slab / existing false ceiling) and available ceiling drop"));
        List<String> notes = new ArrayList<>();
        String req = String.join(" ", userRequirements == null ? List.of() : userRequirements).toLowerCase(Locale.ROOT)
                + " " + (style == null ? "" : style.toLowerCase(Locale.ROOT));
        double cove = 0;
        boolean wantsCove = req.contains("cove") || req.contains("luxury") || req.contains("contemporary") || req.contains("warm");
        if (wantsCove && g.room().height() >= 2700) {
            cove = Math.round(2 * (l + w) / 10) / 100.0;
            fixtures.add(new LightingSchedule.Fixture("CV-1", "AMBIENT", "Cove LED strip (perimeter)", "2700K", (int) (cove * 800),
                    null, null, "False-ceiling cove", "Needs ~250 mm ceiling drop - confirm feasibility"));
        } else if (wantsCove) {
            notes.add("Cove lighting skipped: ceiling height " + (int) g.room().height() + " mm leaves too little room for a drop");
        }
        if (req.contains("profile")) {
            fixtures.add(new LightingSchedule.Fixture("PR-1", "ACCENT", "Recessed aluminium profile light", "3000K", 1200, null, null,
                    "Wall/ceiling profile", "Route along feature wall"));
        }
        for (GeometrySpec.Item it : g.furniture()) {
            double cx = round(it.x() + it.sizeX() / 2);
            double cy = round(it.y() + it.sizeY() / 2);
            switch (it.category()) {
                case "SEATING" -> fixtures.add(new LightingSchedule.Fixture("TK-" + it.id(), "TASK", "Floor reading lamp", "2700K", 600,
                        cx, cy, "Floor", "Beside " + it.name()));
                case "BED" -> fixtures.add(new LightingSchedule.Fixture("TK-" + it.id(), "TASK", "Bedside wall lights (pair)", "2700K", 400,
                        cx, cy, "Wall", "Two-way switching at bed and door"));
                case "DESK" -> fixtures.add(new LightingSchedule.Fixture("TK-" + it.id(), "TASK", "Desk task lamp", "4000K", 500,
                        cx, cy, "Desk", null));
                case "KITCHEN" -> fixtures.add(new LightingSchedule.Fixture("TK-" + it.id(), "TASK", "Under-cabinet LED strip", "4000K", 700,
                        cx, cy, "Under wall cabinets", "Over " + it.name()));
                case "TABLE" -> {
                    if (it.name().toLowerCase(Locale.ROOT).contains("dining")) {
                        fixtures.add(new LightingSchedule.Fixture("TK-" + it.id(), "TASK", "Pendant cluster over dining table", "2700K", 1500,
                                cx, cy, "Pendant, 750 mm above table", null));
                    }
                }
                case "POOJA" -> fixtures.add(new LightingSchedule.Fixture("AC-" + it.id(), "ACCENT", "Concealed backlight for pooja unit", "2700K", 300,
                        cx, cy, "Inside unit", null));
                default -> {
                }
            }
        }
        fixtures.add(new LightingSchedule.Fixture("AC-FW", "ACCENT", "Wall washer for feature wall", "3000K", 500, null, null,
                "Ceiling, 300 mm from wall", null));
        notes.add(String.format(Locale.ROOT, "Target %d lux over %.1f m² -> %d lm ambient (UF 0.6, MF 0.8)", lux, areaM2, lumens));
        return new LightingSchedule(lux, lumens, count, cove, fixtures, missing, notes);
    }

    private static double round(double v) {
        return Math.round(v);
    }
}
