package com.aiinterior.factory.materials;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterior.factory.domain.Budget;
import com.aiinterior.factory.geometry.TestRooms;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoqTest {

    private final SurfaceAreaCalculator areas = new SurfaceAreaCalculator();
    private final BoqCalculator boq = new BoqCalculator();

    @Test
    void quantitiesComeFromGeometry() {
        var a = areas.compute(TestRooms.shell(List.of(), List.of()));
        assertThat(a.floorM2()).isEqualTo(21.32);
        assertThat(a.perimeterM()).isEqualTo(18.6);
        // gross 18.6 x 3.0 = 55.8 minus door 2.1 and window 2.7
        assertThat(a.wallNetM2()).isEqualTo(51.0);
        assertThat(a.skirtingM()).isEqualTo(17.6);
    }

    @Test
    void everyLineIsLabelledAndTotalsAddUp() {
        var g = TestRooms.shell(List.of(), List.of());
        var e = boq.estimate(g, areas.compute(g), 10, 0, new Budget(100000.0, "INR"));
        assertThat(e.disclaimer()).startsWith("INDICATIVE");
        assertThat(e.lines()).allMatch(l -> l.dataSource() != null && l.confidence() != null);
        double sum = e.lines().stream().mapToDouble(BoqLine::lineTotal).sum();
        assertThat(e.subtotal()).isCloseTo(sum, org.assertj.core.data.Offset.offset(0.05));
        assertThat(e.grandTotal()).isCloseTo(e.subtotal() + e.transport() + e.contingency() + e.tax(), org.assertj.core.data.Offset.offset(0.05));
        assertThat(e.budgetStatus()).startsWith("OVER_BUDGET");
    }

    @Test
    void differentCurrencyIsNotSilentlyCompared() {
        var g = TestRooms.shell(List.of(), List.of());
        var e = boq.estimate(g, areas.compute(g), 1, 0, new Budget(5000.0, "USD"));
        assertThat(e.budgetStatus()).startsWith("NOT_COMPARED");
    }

    @Test
    void csvNeutralisesFormulaInjection() {
        var g = TestRooms.shell(List.of(), List.of());
        String csv = BoqCsvWriter.toCsv("=HYPERLINK(\"x\")", boq.estimate(g, areas.compute(g), 1, 0, null));
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(csv.lines().findFirst().orElseThrow()).startsWith("Room,Code,Item");
    }
}
