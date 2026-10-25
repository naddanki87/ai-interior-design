package com.aiinterior.factory.design;

import com.aiinterior.factory.domain.OpeningType;
import com.aiinterior.factory.domain.WallSide;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.geometry.KeepOutZones;
import com.aiinterior.factory.geometry.Priority;
import com.aiinterior.factory.geometry.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic furniture placement: items back onto walls, slide along them in 50 mm steps
 * and are accepted only if they stay inside the room, avoid every other item and its clearance
 * zone, keep door swings free and keep tall items out of window zones. Nothing is resized.
 */
@Component
public class LayoutPlanner {

    private static final double STEP = 50;

    /** A shell spec (room, walls, openings, fixed objects) to place furniture into. */
    public List<LayoutAlternative> alternatives(GeometrySpec shell, List<FurnitureSpec> items) {
        List<WallSide> byFreeLength = wallsBySolidLength(shell);
        List<WallSide> rotated = new ArrayList<>(byFreeLength.subList(1, byFreeLength.size()));
        rotated.add(byFreeLength.get(0));
        List<LayoutAlternative> out = new ArrayList<>();
        out.add(place(shell, items, byFreeLength, "alt-a", "Option A - anchor on the longest solid wall",
                "Largest pieces go against the wall with the most uninterrupted length."));
        out.add(place(shell, items, rotated, "alt-b", "Option B - rotated arrangement",
                "Main piece moves to the second-best wall to open up a different circulation path."));
        return out;
    }

    private LayoutAlternative place(GeometrySpec shell, List<FurnitureSpec> specs, List<WallSide> wallOrder,
                                    String id, String name, String strategy) {
        double l = shell.room().length();
        double w = shell.room().width();
        List<GeometrySpec.Item> placed = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        Map<String, GeometrySpec.Item> byKey = new HashMap<>();
        List<KeepOutZones.Zone> zones = KeepOutZones.of(shell);

        // Stable sort by priority keeps catalogue order, so anchored pieces (TV unit, coffee table)
        // are placed right after the piece they relate to.
        List<FurnitureSpec> ordered = new ArrayList<>(specs);
        ordered.sort(Comparator.comparingInt((FurnitureSpec f) -> rank(f.priority())));

        for (FurnitureSpec f : ordered) {
            GeometrySpec.Item item = switch (f.placement()) {
                case WALL -> onWalls(f, wallOrder, shell, placed, zones, l, w);
                case OPPOSITE_ANCHOR -> {
                    GeometrySpec.Item anchor = byKey.get(f.anchor());
                    List<WallSide> order = new ArrayList<>(wallOrder);
                    if (anchor != null && anchor.againstWall() != null) {
                        order.remove(opposite(anchor.againstWall()));
                        order.add(0, opposite(anchor.againstWall()));
                    }
                    yield onWalls(f, order, shell, placed, zones, l, w);
                }
                case IN_FRONT_OF_ANCHOR -> inFront(f, byKey.get(f.anchor()), shell, placed, zones, l, w);
                case CENTER -> centre(f, shell, placed, zones, l, w);
            };
            if (item == null) {
                unplaced.add(f.name() + " (" + f.priority() + ") - no position satisfies clearances, doors and windows");
            } else {
                placed.add(item);
                byKey.put(f.key(), item);
            }
        }
        long requiredMissing = specs.stream().filter(s -> s.priority() == Priority.REQUIRED)
                .filter(s -> !byKey.containsKey(s.key())).count();
        int score = (int) (100 - requiredMissing * 30 - (unplaced.size() - requiredMissing) * 5);
        return new LayoutAlternative(id, name, strategy, placed, unplaced, null, null, score);
    }

    private static int rank(Priority p) {
        return switch (p) {
            case MUST_NOT_CHANGE -> 0;
            case REQUIRED -> 1;
            case PREFERRED -> 2;
            case OPTIONAL -> 3;
        };
    }

    private GeometrySpec.Item onWalls(FurnitureSpec f, List<WallSide> order, GeometrySpec shell,
                                      List<GeometrySpec.Item> placed, List<KeepOutZones.Zone> zones, double l, double w) {
        for (WallSide side : order) {
            double wallLen = side.length(l, w);
            if (f.width() > wallLen) {
                continue;
            }
            double centre = (wallLen - f.width()) / 2;
            int steps = (int) Math.floor(centre / STEP) + 1;
            for (int k = 0; k <= steps * 2; k++) {
                double u = centre + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * STEP;
                if (k == 0) {
                    u = centre;
                }
                u = Math.max(0, Math.min(wallLen - f.width(), u));
                GeometrySpec.Item candidate = atWall(f, side, u, l, w);
                if (fits(candidate, shell, placed, zones, l, w)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    static GeometrySpec.Item atWall(FurnitureSpec f, WallSide side, double u, double l, double w) {
        return switch (side) {
            case SOUTH -> item(f, u, 0, f.width(), f.depth(), side, "+Y");
            case NORTH -> item(f, l - u - f.width(), w - f.depth(), f.width(), f.depth(), side, "-Y");
            case WEST -> item(f, 0, w - u - f.width(), f.depth(), f.width(), side, "+X");
            case EAST -> item(f, l - f.depth(), u, f.depth(), f.width(), side, "-X");
        };
    }

    private GeometrySpec.Item inFront(FurnitureSpec f, GeometrySpec.Item anchor, GeometrySpec shell,
                                      List<GeometrySpec.Item> placed, List<KeepOutZones.Zone> zones, double l, double w) {
        if (anchor == null) {
            return centre(f, shell, placed, zones, l, w);
        }
        // Sits inside the anchor's front zone, at the anchor's clearance distance (e.g. sofa -> coffee table).
        double gap = "RUG".equals(f.category()) ? -200 : Math.max(350, anchor.frontClearance());
        double ax = anchor.x() + anchor.sizeX() / 2;
        double ay = anchor.y() + anchor.sizeY() / 2;
        GeometrySpec.Item c = switch (anchor.facing()) {
            case "+Y" -> item(f, ax - f.width() / 2, anchor.y() + anchor.sizeY() + gap, f.width(), f.depth(), null, "-Y");
            case "-Y" -> item(f, ax - f.width() / 2, anchor.y() - gap - f.depth(), f.width(), f.depth(), null, "+Y");
            case "+X" -> item(f, anchor.x() + anchor.sizeX() + gap, ay - f.width() / 2, f.depth(), f.width(), null, "-X");
            default -> item(f, anchor.x() - gap - f.depth(), ay - f.width() / 2, f.depth(), f.width(), null, "+X");
        };
        return fits(c, shell, placed, zones, l, w, anchor) ? c : null;
    }

    private GeometrySpec.Item centre(FurnitureSpec f, GeometrySpec shell, List<GeometrySpec.Item> placed,
                                     List<KeepOutZones.Zone> zones, double l, double w) {
        double cx = (l - f.width()) / 2;
        double cy = (w - f.depth()) / 2;
        for (int r = 0; r < 30; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    GeometrySpec.Item c = item(f, cx + dx * 100, cy + dy * 100, f.width(), f.depth(), null, null);
                    if (fits(c, shell, placed, zones, l, w)) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private boolean fits(GeometrySpec.Item c, GeometrySpec shell, List<GeometrySpec.Item> placed,
                         List<KeepOutZones.Zone> zones, double l, double w) {
        return fits(c, shell, placed, zones, l, w, null);
    }

    /** {@code anchor}: the item {@code c} deliberately sits in front of; its clearance zone is not an obstacle for {@code c}. */
    private boolean fits(GeometrySpec.Item c, GeometrySpec shell, List<GeometrySpec.Item> placed,
                         List<KeepOutZones.Zone> zones, double l, double w, GeometrySpec.Item anchor) {
        Rect fp = c.footprint();
        if (!fp.within(l, w) || c.height() > shell.room().height()) {
            return false;
        }
        Rect clr = c.clearanceZone();
        if (clr != null && !clr.within(l, w)) {
            return false;
        }
        boolean rug = "RUG".equals(c.category());
        List<GeometrySpec.Item> others = new ArrayList<>(shell.fixedObjects());
        others.addAll(placed);
        for (GeometrySpec.Item o : others) {
            if (rug || "RUG".equals(o.category())) {
                continue;
            }
            if (fp.intersects(o.footprint())) {
                return false;
            }
            Rect oc = o == anchor ? null : o.clearanceZone();
            if (oc != null && fp.intersects(oc)) {
                return false;
            }
            if (clr != null && clr.intersects(o.footprint())) {
                return false;
            }
        }
        for (KeepOutZones.Zone z : zones) {
            if (rug && z.type() != OpeningType.WINDOW) {
                continue;
            }
            if (fp.intersects(z.rect()) && c.height() > z.maxItemHeight()) {
                return false;
            }
        }
        return true;
    }

    private static List<WallSide> wallsBySolidLength(GeometrySpec shell) {
        Map<WallSide, Double> free = new HashMap<>();
        for (GeometrySpec.Wall wall : shell.walls()) {
            double openings = shell.openings().stream().filter(o -> o.wallId().equals(wall.id()))
                    .mapToDouble(GeometrySpec.Opening::width).sum();
            free.put(wall.side(), wall.length() - openings * 1.5);
        }
        List<WallSide> order = new ArrayList<>(List.of(WallSide.values()));
        order.sort(Comparator.comparingDouble((WallSide s) -> free.getOrDefault(s, 0.0)).reversed());
        return order;
    }

    private static WallSide opposite(WallSide s) {
        return switch (s) {
            case SOUTH -> WallSide.NORTH;
            case NORTH -> WallSide.SOUTH;
            case EAST -> WallSide.WEST;
            case WEST -> WallSide.EAST;
        };
    }

    private static GeometrySpec.Item item(FurnitureSpec f, double x, double y, double sx, double sy, WallSide wall, String facing) {
        return new GeometrySpec.Item("fur-" + f.key().replace('_', '-'), f.name(), f.category(),
                round(x), round(y), sx, sy, f.height(), wall, facing, f.frontClearance(), f.priority(), f.source());
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
