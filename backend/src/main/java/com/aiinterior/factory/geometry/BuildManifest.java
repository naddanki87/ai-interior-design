package com.aiinterior.factory.geometry;

import java.util.List;

/**
 * Geometry reduced to solid boxes - the common input for the OBJ writer, the SVG plan
 * and the SketchUp Ruby builder, so every output is built from the same numbers.
 */
public record BuildManifest(String schemaVersion, String units, GeometrySpec.Room room, List<Box> boxes) {

    public record Box(String id, String name, String tag, String material, int[] color,
                      double[] min, double[] max) {
    }
}
