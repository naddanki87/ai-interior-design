package com.aiinterior.factory.geometry;

import com.aiinterior.factory.domain.DimensionSource;
import com.aiinterior.factory.domain.OpeningType;
import com.aiinterior.factory.domain.WallSide;
import java.util.List;

/**
 * Canonical, versioned geometry. All values are millimetres in the room coordinate system
 * documented on {@link WallSide}. This - never an image - is the source of truth for exports.
 */
public record GeometrySpec(
        String schemaVersion,
        String sessionId,
        int designVersion,
        String units,
        String coordinateSystem,
        Room room,
        List<Wall> walls,
        List<Opening> openings,
        List<Item> fixedObjects,
        List<Item> furniture,
        List<String> constraints,
        List<MeasurementSource> measurementSources,
        String approvalStatus) {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String COORDINATES = "Origin at inner SOUTH-WEST floor corner; +X along room length (SOUTH wall), "
            + "+Y along room width (WEST wall), +Z up; walls counter-clockwise SOUTH, EAST, NORTH, WEST; "
            + "opening offsets measured from each wall's start point.";

    public record Room(double length, double width, double height) {
    }

    public record Wall(String id, WallSide side, double startX, double startY, double endX, double endY,
                       double thickness, double height) {

        public double length() {
            return Math.hypot(endX - startX, endY - startY);
        }
    }

    public record Opening(String id, OpeningType type, String wallId, double offset, double width, double height,
                          double sillHeight, String label) {
    }

    /** Furniture or a fixed object; footprint is axis-aligned, {@code facing} is the direction its front faces. */
    public record Item(String id, String name, String category, double x, double y, double sizeX, double sizeY,
                       double height, WallSide againstWall, String facing, double frontClearance,
                       Priority priority, String source) {

        public Rect footprint() {
            return new Rect(x, y, sizeX, sizeY);
        }

        /** The zone in front of the item that must stay free for use and movement. */
        public Rect clearanceZone() {
            if (frontClearance <= 0 || facing == null) {
                return null;
            }
            return switch (facing) {
                case "+Y" -> new Rect(x, y + sizeY, sizeX, frontClearance);
                case "-Y" -> new Rect(x, y - frontClearance, sizeX, frontClearance);
                case "+X" -> new Rect(x + sizeX, y, frontClearance, sizeY);
                case "-X" -> new Rect(x - frontClearance, y, frontClearance, sizeY);
                default -> null;
            };
        }
    }

    public record MeasurementSource(String field, Double valueMm, DimensionSource source, String originalInput) {
    }
}
