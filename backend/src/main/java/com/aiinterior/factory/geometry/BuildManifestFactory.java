package com.aiinterior.factory.geometry;

import com.aiinterior.factory.domain.WallSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts walls with openings into wall segments (no boolean operations needed downstream). */
@Component
public class BuildManifestFactory {

    private static final Map<String, int[]> COLORS = Map.of(
            "Wall", new int[]{236, 231, 222},
            "Floor", new int[]{196, 170, 135},
            "Furniture", new int[]{150, 120, 95},
            "Fixed", new int[]{120, 128, 140},
            "Rug", new int[]{176, 98, 74});

    public BuildManifest build(GeometrySpec spec) {
        List<BuildManifest.Box> boxes = new ArrayList<>();
        double l = spec.room().length();
        double w = spec.room().width();
        double h = spec.room().height();
        double floorT = 50;
        boxes.add(box("floor", "Floor slab", "AIDF Floor", "Floor", 0, 0, -floorT, l, w, 0));

        for (GeometrySpec.Wall wall : spec.walls()) {
            double t = wall.thickness();
            double len = wall.length();
            List<GeometrySpec.Opening> ops = spec.openings().stream()
                    .filter(o -> o.wallId().equals(wall.id()))
                    .sorted(Comparator.comparingDouble(GeometrySpec.Opening::offset)).toList();
            // Walls are extended by their thickness at both ends so corners close.
            List<double[]> segs = new ArrayList<>(); // {u0,u1,z0,z1} in wall-local coordinates
            double cursor = -t;
            for (GeometrySpec.Opening o : ops) {
                segs.add(new double[]{cursor, o.offset(), 0, h});
                if (o.sillHeight() > 0) {
                    segs.add(new double[]{o.offset(), o.offset() + o.width(), 0, o.sillHeight()});
                }
                double top = o.sillHeight() + o.height();
                if (top < h) {
                    segs.add(new double[]{o.offset(), o.offset() + o.width(), top, h});
                }
                cursor = o.offset() + o.width();
            }
            segs.add(new double[]{cursor, len + t, 0, h});
            int n = 0;
            for (double[] s : segs) {
                if (s[1] - s[0] < 0.5 || s[3] - s[2] < 0.5) {
                    continue;
                }
                double[] r = wallRect(wall.side(), s[0], s[1], t, l, w);
                boxes.add(box(wall.id() + "-seg" + (++n), wall.side() + " wall", "AIDF Walls", "Wall", r[0], r[1], s[2], r[2], r[3], s[3]));
            }
        }
        for (GeometrySpec.Item it : spec.fixedObjects()) {
            boxes.add(item(it, "AIDF Fixed (must not change)", "Fixed"));
        }
        for (GeometrySpec.Item it : spec.furniture()) {
            boxes.add(item(it, "AIDF Furniture", "RUG".equals(it.category()) ? "Rug" : "Furniture"));
        }
        return new BuildManifest(spec.schemaVersion(), "mm", spec.room(), boxes);
    }

    /** Returns {minX,minY,maxX,maxY} of a wall segment spanning [u0,u1] along the wall, outside the room. */
    private static double[] wallRect(WallSide side, double u0, double u1, double t, double l, double w) {
        return switch (side) {
            case SOUTH -> new double[]{u0, -t, u1, 0};
            case EAST -> new double[]{l, u0, l + t, u1};
            case NORTH -> new double[]{l - u1, w, l - u0, w + t};
            case WEST -> new double[]{-t, w - u1, 0, w - u0};
        };
    }

    private static BuildManifest.Box item(GeometrySpec.Item it, String tag, String material) {
        return box(it.id(), it.name(), tag, material, it.x(), it.y(), 0, it.x() + it.sizeX(), it.y() + it.sizeY(), it.height());
    }

    private static BuildManifest.Box box(String id, String name, String tag, String material,
                                         double x0, double y0, double z0, double x1, double y1, double z1) {
        return new BuildManifest.Box(id, name, tag, material, COLORS.get(material),
                new double[]{x0, y0, z0}, new double[]{x1, y1, z1});
    }
}
