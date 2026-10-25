package com.aiinterior.factory.domain;

/**
 * Coordinate convention (millimetres, right-handed, Z up):
 * origin = inner corner where the SOUTH and WEST walls meet, X runs along the room length,
 * Y along the room width. Walls are walked counter-clockwise seen from above:
 * SOUTH (0,0)->(L,0), EAST (L,0)->(L,W), NORTH (L,W)->(0,W), WEST (0,W)->(0,0).
 * An opening offset is measured from the wall's start point, i.e. from the right-hand
 * corner when you stand inside the room facing that wall.
 */
public enum WallSide {
    SOUTH, EAST, NORTH, WEST;

    public double length(double roomLength, double roomWidth) {
        return this == SOUTH || this == NORTH ? roomLength : roomWidth;
    }
}
