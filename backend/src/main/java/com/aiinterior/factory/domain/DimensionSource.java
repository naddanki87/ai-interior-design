package com.aiinterior.factory.domain;

/** Where a dimension came from. Only MEASURED and DRAWING values are treated as verified. */
public enum DimensionSource {
    MEASURED, DRAWING, AI_ESTIMATED, USER_ASSUMPTION, UNKNOWN;

    public boolean verified() {
        return this == MEASURED || this == DRAWING;
    }
}
