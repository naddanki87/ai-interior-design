package com.aiinterior.factory.units;

import com.aiinterior.factory.domain.Dimension;
import com.aiinterior.factory.domain.DimensionSource;
import com.aiinterior.factory.domain.LengthInput;
import com.aiinterior.factory.domain.LengthUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic conversion of user lengths to millimetres (1 in = 25.4 mm exactly).
 * Accepts "4200", "4200mm", "420 cm", "4.2m", "13'", "13' 9\"", "13'-9.5\"", "13ft 9in", "9 in".
 * Never guesses: anything it cannot parse is rejected with a message.
 */
public final class LengthParser {

    private static final Pattern METRIC = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(mm|cm|m)?$");
    private static final Pattern IMPERIAL = Pattern.compile(
            "^(?:([0-9]+(?:\\.[0-9]+)?)\\s*(?:'|ft|feet|foot))?\\s*-?\\s*(?:([0-9]+(?:\\.[0-9]+)?)\\s*(?:\"|in|inch|inches))?$");

    private LengthParser() {
    }

    public static double toMm(String raw, LengthUnit unit) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Length is empty");
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("’", "'").replace("”", "\"").replace("″", "\"").replace("′", "'");
        if (s.startsWith("-")) {
            throw new IllegalArgumentException("Length cannot be negative: " + raw);
        }
        Matcher m = METRIC.matcher(s);
        if (m.matches() && (m.group(2) != null || unit != LengthUnit.FT_IN)) {
            BigDecimal v = new BigDecimal(m.group(1));
            String suffix = m.group(2);
            LengthUnit effective = suffix == null ? (unit == null ? LengthUnit.MM : unit)
                    : switch (suffix) {
                        case "mm" -> LengthUnit.MM;
                        case "cm" -> LengthUnit.CM;
                        default -> LengthUnit.M;
                    };
            BigDecimal mm = switch (effective) {
                case MM -> v;
                case CM -> v.multiply(BigDecimal.TEN);
                case M -> v.multiply(BigDecimal.valueOf(1000));
                case FT_IN -> v.multiply(new BigDecimal("304.8"));
            };
            return round(mm);
        }
        Matcher im = IMPERIAL.matcher(s);
        if (im.matches() && (im.group(1) != null || im.group(2) != null)) {
            BigDecimal feet = im.group(1) == null ? BigDecimal.ZERO : new BigDecimal(im.group(1));
            BigDecimal inches = im.group(2) == null ? BigDecimal.ZERO : new BigDecimal(im.group(2));
            return round(feet.multiply(new BigDecimal("304.8")).add(inches.multiply(new BigDecimal("25.4"))));
        }
        if (unit == LengthUnit.FT_IN && METRIC.matcher(s).matches()) {
            return round(new BigDecimal(s).multiply(new BigDecimal("304.8")));
        }
        throw new IllegalArgumentException("Cannot understand length '" + raw + "'. Use e.g. 4200 mm, 420 cm, 4.2 m or 13' 9\"");
    }

    public static Dimension toDimension(LengthInput input) {
        if (input == null || input.blank()) {
            return Dimension.unknown();
        }
        DimensionSource source = input.source() == null ? DimensionSource.MEASURED : input.source();
        return new Dimension(toMm(input.value(), input.unit()), source, input.value() + (input.unit() == null ? "" : " " + input.unit()));
    }

    private static double round(BigDecimal mm) {
        return mm.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
