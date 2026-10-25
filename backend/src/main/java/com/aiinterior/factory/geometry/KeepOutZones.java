package com.aiinterior.factory.geometry;

import com.aiinterior.factory.domain.OpeningType;
import java.util.ArrayList;
import java.util.List;

/** Floor areas derived from openings that furniture must respect. */
public final class KeepOutZones {

    /** Depth kept free in front of a window for tall furniture. */
    public static final double WINDOW_ZONE_DEPTH = 450;

    private KeepOutZones() {
    }

    public record Zone(String openingId, OpeningType type, Rect rect, double maxItemHeight) {
    }

    /**
     * Doors and open passages reserve a square (door swing / passage) inside the room; nothing may sit there.
     * Windows reserve a shallow strip where only items lower than the sill are allowed.
     */
    public static List<Zone> of(GeometrySpec spec) {
        List<Zone> zones = new ArrayList<>();
        double l = spec.room().length();
        double w = spec.room().width();
        for (GeometrySpec.Opening o : spec.openings()) {
            GeometrySpec.Wall wall = spec.walls().stream().filter(x -> x.id().equals(o.wallId())).findFirst().orElse(null);
            if (wall == null) {
                continue;
            }
            double depth = o.type() == OpeningType.WINDOW ? WINDOW_ZONE_DEPTH : Math.max(o.width(), 900);
            double maxH = o.type() == OpeningType.WINDOW ? o.sillHeight() : 0;
            Rect r = switch (wall.side()) {
                case SOUTH -> new Rect(o.offset(), 0, o.width(), depth);
                case EAST -> new Rect(l - depth, o.offset(), depth, o.width());
                case NORTH -> new Rect(l - o.offset() - o.width(), w - depth, o.width(), depth);
                case WEST -> new Rect(0, w - o.offset() - o.width(), depth, o.width());
            };
            zones.add(new Zone(o.id(), o.type(), r, maxH));
        }
        return zones;
    }
}
