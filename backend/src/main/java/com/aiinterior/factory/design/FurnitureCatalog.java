package com.aiinterior.factory.design;

import com.aiinterior.factory.design.FurnitureSpec.Placement;
import com.aiinterior.factory.domain.RoomType;
import com.aiinterior.factory.geometry.Priority;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Typical furniture sizes (mm) and the default set per room type. */
@Component
public class FurnitureCatalog {

    private static final String STD = "STANDARD_SIZE";
    private static final Map<String, FurnitureSpec> ITEMS = new LinkedHashMap<>();

    static {
        add("sofa", "3-seater sofa", "SEATING", 2100, 900, 850, 450, Placement.WALL, null);
        add("tv_unit", "TV unit", "STORAGE", 1800, 450, 500, 900, Placement.OPPOSITE_ANCHOR, "sofa");
        add("coffee_table", "Coffee table", "TABLE", 1100, 600, 420, 0, Placement.IN_FRONT_OF_ANCHOR, "sofa");
        add("rug", "Area rug", "RUG", 2400, 1700, 10, 0, Placement.IN_FRONT_OF_ANCHOR, "sofa");
        add("armchair", "Armchair", "SEATING", 800, 800, 850, 450, Placement.WALL, null);
        add("bookshelf", "Bookshelf", "STORAGE", 1200, 350, 2000, 700, Placement.WALL, null);
        add("king_bed", "King bed with bedside tables", "BED", 2950, 2150, 1000, 600, Placement.WALL, null);
        add("queen_bed", "Queen bed with bedside tables", "BED", 2500, 2100, 950, 600, Placement.WALL, null);
        add("low_queen_bed", "Queen bed (low, easy-access) with bedside tables", "BED", 2500, 2100, 900, 750, Placement.WALL, null);
        add("single_bed", "Single bed", "BED", 1050, 2050, 900, 600, Placement.WALL, null);
        add("wardrobe", "Wardrobe (sliding)", "STORAGE", 2400, 600, 2400, 900, Placement.WALL, null);
        add("wardrobe_small", "Wardrobe", "STORAGE", 1800, 600, 2200, 900, Placement.WALL, null);
        add("dresser", "Dresser", "STORAGE", 1200, 450, 750, 700, Placement.WALL, null);
        add("study_desk", "Study desk with chair", "DESK", 1200, 600, 750, 800, Placement.WALL, null);
        add("office_desk", "Work desk with chair", "DESK", 1500, 700, 750, 900, Placement.WALL, null);
        add("easy_chair", "Easy chair", "SEATING", 750, 800, 900, 500, Placement.WALL, null);
        add("base_counter", "Base cabinets + countertop", "KITCHEN", 2400, 600, 850, 1000, Placement.WALL, null);
        add("base_counter_2", "Second counter run", "KITCHEN", 1800, 600, 850, 1000, Placement.WALL, null);
        add("tall_unit", "Tall unit / refrigerator", "KITCHEN", 750, 700, 2100, 1000, Placement.WALL, null);
        add("washing_machine", "Washing machine", "APPLIANCE", 600, 600, 850, 700, Placement.WALL, null);
        add("utility_sink", "Utility sink counter", "KITCHEN", 1000, 600, 850, 700, Placement.WALL, null);
        add("utility_tall", "Utility storage", "STORAGE", 600, 400, 2000, 600, Placement.WALL, null);
        add("wc", "Wall-hung WC", "SANITARY", 400, 700, 800, 600, Placement.WALL, null);
        add("vanity", "Vanity with basin", "SANITARY", 900, 500, 850, 700, Placement.WALL, null);
        add("shower", "Shower zone", "ZONE", 900, 900, 20, 0, Placement.WALL, null);
        add("pooja_unit", "Pooja unit", "POOJA", 1200, 450, 2100, 900, Placement.WALL, null);
        add("floor_seating", "Floor seating mat", "RUG", 1200, 900, 10, 0, Placement.IN_FRONT_OF_ANCHOR, "pooja_unit");
        add("shoe_cabinet", "Shoe cabinet", "STORAGE", 1200, 350, 1000, 600, Placement.WALL, null);
        add("console", "Console table", "TABLE", 1000, 350, 800, 600, Placement.WALL, null);
        add("bench", "Entry bench", "SEATING", 900, 400, 450, 500, Placement.WALL, null);
        add("balcony_seating", "Balcony seating set", "SEATING", 1500, 700, 800, 600, Placement.WALL, null);
        add("planter", "Planter", "DECOR", 400, 400, 600, 0, Placement.WALL, null);
        add("swing", "Swing chair", "SEATING", 1000, 1000, 1900, 600, Placement.WALL, null);
        add("dining_table", "Dining table with 6 chairs", "TABLE", 1800, 2000, 750, 0, Placement.CENTER, null);
        add("crockery_unit", "Crockery unit", "STORAGE", 1500, 450, 900, 900, Placement.WALL, null);
        add("side_table", "Side table", "TABLE", 450, 450, 550, 0, Placement.WALL, null);
    }

    private static final Map<RoomType, List<String[]>> DEFAULTS = Map.ofEntries(
            Map.entry(RoomType.LIVING_ROOM, rows("sofa:R", "tv_unit:R", "coffee_table:P", "rug:P", "armchair:O", "bookshelf:O")),
            Map.entry(RoomType.MASTER_BEDROOM, rows("king_bed:R", "wardrobe:R", "dresser:P", "easy_chair:O")),
            Map.entry(RoomType.GUEST_BEDROOM, rows("queen_bed:R", "wardrobe_small:R", "dresser:O")),
            Map.entry(RoomType.CHILDRENS_BEDROOM, rows("single_bed:R", "wardrobe_small:R", "study_desk:R", "bookshelf:O")),
            Map.entry(RoomType.GRANDPARENTS_ROOM, rows("low_queen_bed:R", "wardrobe_small:R", "easy_chair:P")),
            Map.entry(RoomType.KITCHEN, rows("base_counter:R", "tall_unit:R", "base_counter_2:P")),
            Map.entry(RoomType.UTILITY_BALCONY, rows("washing_machine:R", "utility_sink:R", "utility_tall:O")),
            Map.entry(RoomType.BATHROOM, rows("wc:R", "vanity:R", "shower:R")),
            Map.entry(RoomType.POOJA_ROOM, rows("pooja_unit:R", "floor_seating:P")),
            Map.entry(RoomType.FOYER, rows("shoe_cabinet:R", "console:O", "bench:O")),
            Map.entry(RoomType.BALCONY, rows("balcony_seating:R", "planter:P", "swing:O")),
            Map.entry(RoomType.DINING_ROOM, rows("dining_table:R", "crockery_unit:P")),
            Map.entry(RoomType.STUDY, rows("study_desk:R", "bookshelf:R", "easy_chair:O")),
            Map.entry(RoomType.HOME_OFFICE, rows("office_desk:R", "bookshelf:P", "armchair:O")));

    private static final Pattern QTY = Pattern.compile("^\\s*(\\d+)\\s*(?:x|nos?\\.?|pcs?)?\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    private static void add(String key, String name, String cat, double w, double d, double h, double clr,
                            Placement p, String anchor) {
        ITEMS.put(key, new FurnitureSpec(key, name, cat, w, d, h, clr, p, anchor, Priority.PREFERRED, STD, null));
    }

    private static List<String[]> rows(String... r) {
        return java.util.Arrays.stream(r).map(s -> s.split(":")).toList();
    }

    /** Default set for a room type plus the user's explicit requests (which become REQUIRED). */
    public List<FurnitureSpec> proposeFor(RoomType type, List<String> requests) {
        Map<String, FurnitureSpec> out = new LinkedHashMap<>();
        for (String[] row : DEFAULTS.getOrDefault(type, List.of())) {
            Priority p = switch (row[1]) {
                case "R" -> Priority.REQUIRED;
                case "P" -> Priority.PREFERRED;
                default -> Priority.OPTIONAL;
            };
            out.put(row[0], ITEMS.get(row[0]).withPriority(p, "Typical for a " + type.label().toLowerCase(Locale.ROOT)));
        }
        int custom = 0;
        java.util.Set<String> claimed = new java.util.HashSet<>();
        for (String raw : requests) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int qty = 1;
            String text = raw.trim();
            Matcher m = QTY.matcher(text);
            if (m.matches()) {
                qty = Math.min(6, Integer.parseInt(m.group(1)));
                text = m.group(2);
            }
            FurnitureSpec match = match(text);
            for (int i = 0; i < qty; i++) {
                if (match != null) {
                    // The first request for a default piece claims it (upgrading it to REQUIRED);
                    // any further copies get numbered keys.
                    String key = match.key();
                    if (claimed.contains(key)) {
                        int n = 2;
                        while (out.containsKey(match.key() + "_" + n)) {
                            n++;
                        }
                        key = match.key() + "_" + n;
                    }
                    claimed.add(key);
                    out.put(key, match.withKey(key).withPriority(Priority.REQUIRED, "Requested by you: \"" + raw.trim() + "\""));
                } else {
                    custom++;
                    String key = "custom_" + custom;
                    out.put(key, new FurnitureSpec(key, capitalise(text), "CUSTOM", 600, 600, 750, 500, Placement.WALL, null,
                            Priority.REQUIRED, "CUSTOM_ASSUMED_SIZE",
                            "Requested by you; size 600x600x750 mm is an assumption - please provide real dimensions"));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    public FurnitureSpec match(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        String[][] keywords = {
                {"sofa", "sofa"}, {"couch", "sofa"}, {"tv", "tv_unit"}, {"coffee", "coffee_table"}, {"rug", "rug"},
                {"armchair", "armchair"}, {"accent chair", "armchair"}, {"book", "bookshelf"}, {"king", "king_bed"},
                {"queen", "queen_bed"}, {"single bed", "single_bed"}, {"bunk", "single_bed"}, {"wardrobe", "wardrobe"},
                {"dresser", "dresser"}, {"vanity", "vanity"}, {"study", "study_desk"}, {"desk", "office_desk"},
                {"easy chair", "easy_chair"}, {"fridge", "tall_unit"}, {"refrigerator", "tall_unit"},
                {"counter", "base_counter_2"}, {"washing", "washing_machine"}, {"sink", "utility_sink"},
                {"wc", "wc"}, {"toilet", "wc"}, {"shower", "shower"}, {"pooja", "pooja_unit"}, {"mandir", "pooja_unit"},
                {"shoe", "shoe_cabinet"}, {"console", "console"}, {"bench", "bench"}, {"swing", "swing"},
                {"jhula", "swing"}, {"planter", "planter"}, {"plant", "planter"}, {"dining", "dining_table"},
                {"crockery", "crockery_unit"}, {"side table", "side_table"}, {"bed", "queen_bed"}};
        for (String[] kw : keywords) {
            if (t.contains(kw[0])) {
                return ITEMS.get(kw[1]);
            }
        }
        return null;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
