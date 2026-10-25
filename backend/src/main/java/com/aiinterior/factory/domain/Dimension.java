package com.aiinterior.factory.domain;

/** A length in millimetres with its provenance. {@code mm == null} means unknown. */
public record Dimension(Double mm, DimensionSource source, String originalInput) {

    public static Dimension unknown() {
        return new Dimension(null, DimensionSource.UNKNOWN, null);
    }

    public boolean known() {
        return mm != null;
    }

    public double value() {
        if (mm == null) {
            throw new IllegalStateException("Dimension is unknown");
        }
        return mm;
    }
}
