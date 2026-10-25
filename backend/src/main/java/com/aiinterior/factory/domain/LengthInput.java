package com.aiinterior.factory.domain;

/**
 * Raw user input for one length, e.g. {"value":"12' 6\"", "unit":"FT_IN"} or {"value":"4.2","unit":"M"}.
 * The unit may be omitted when the value carries its own suffix ("4200mm", "4.2 m", "13ft 9in").
 */
public record LengthInput(String value, LengthUnit unit, DimensionSource source) {

    public static LengthInput mm(double v) {
        return new LengthInput(String.valueOf(v), LengthUnit.MM, DimensionSource.MEASURED);
    }

    public boolean blank() {
        return value == null || value.isBlank();
    }
}
