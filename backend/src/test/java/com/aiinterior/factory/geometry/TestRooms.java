package com.aiinterior.factory.geometry;

import com.aiinterior.factory.domain.OpeningType;
import com.aiinterior.factory.domain.WallSide;
import java.util.List;

public final class TestRooms {

    private TestRooms() {
    }

    /** 5200 x 4100 x 3000 room, door on SOUTH at 300, window on EAST at 1000 (sill 750). */
    public static GeometrySpec shell(List<GeometrySpec.Item> fixed, List<GeometrySpec.Item> furniture) {
        double l = 5200;
        double w = 4100;
        double h = 3000;
        return new GeometrySpec(GeometrySpec.SCHEMA_VERSION, "ses_test00000000", 1, "mm", GeometrySpec.COORDINATES,
                new GeometrySpec.Room(l, w, h),
                List.of(new GeometrySpec.Wall("wall-s", WallSide.SOUTH, 0, 0, l, 0, 115, h),
                        new GeometrySpec.Wall("wall-e", WallSide.EAST, l, 0, l, w, 115, h),
                        new GeometrySpec.Wall("wall-n", WallSide.NORTH, l, w, 0, w, 115, h),
                        new GeometrySpec.Wall("wall-w", WallSide.WEST, 0, w, 0, 0, 115, h)),
                List.of(new GeometrySpec.Opening("op-1", OpeningType.DOOR, "wall-s", 300, 1000, 2100, 0, "Door"),
                        new GeometrySpec.Opening("op-2", OpeningType.WINDOW, "wall-e", 1000, 1800, 1500, 750, "Window")),
                fixed, furniture, List.of(), List.of(), "PENDING");
    }

    public static GeometrySpec.Item box(String id, double x, double y, double sx, double sy, double h) {
        return new GeometrySpec.Item(id, id, "TABLE", x, y, sx, sy, h, null, null, 0, Priority.REQUIRED, "STANDARD_SIZE");
    }
}
