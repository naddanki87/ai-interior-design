package com.aiinterior.factory.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterior.factory.domain.DimensionSource;
import com.aiinterior.factory.domain.LengthInput;
import com.aiinterior.factory.domain.LengthUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LengthParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "4200|MM|4200.0", "420|CM|4200.0", "4.2|M|4200.0", "4200mm||4200.0", "420 cm||4200.0", "4.2 m||4200.0",
            "13'||3962.4", "13' 9\"||4191.0", "13'-9.5\"||4203.7", "13ft 9in||4191.0", "9 in||228.6", "13|FT_IN|3962.4",
            "0|MM|0.0"})
    void convertsDeterministically(String raw, LengthUnit unit, double expectedMm) {
        assertThat(LengthParser.toMm(raw, unit)).isEqualTo(expectedMm);
    }

    @Test
    void rejectsGarbageAndNegatives() {
        assertThatThrownBy(() -> LengthParser.toMm("about four metres", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LengthParser.toMm("-200", LengthUnit.MM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LengthParser.toMm(" ", LengthUnit.MM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankInputIsUnknownNotZero() {
        var d = LengthParser.toDimension(new LengthInput("", LengthUnit.MM, DimensionSource.MEASURED));
        assertThat(d.known()).isFalse();
        assertThat(d.source()).isEqualTo(DimensionSource.UNKNOWN);
    }

    @Test
    void keepsProvenance() {
        var d = LengthParser.toDimension(new LengthInput("3.1", LengthUnit.M, DimensionSource.USER_ASSUMPTION));
        assertThat(d.mm()).isEqualTo(3100.0);
        assertThat(d.source()).isEqualTo(DimensionSource.USER_ASSUMPTION);
        assertThat(d.source().verified()).isFalse();
    }
}
