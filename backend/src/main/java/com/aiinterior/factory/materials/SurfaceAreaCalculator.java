package com.aiinterior.factory.materials;

import com.aiinterior.factory.domain.OpeningType;
import com.aiinterior.factory.geometry.GeometrySpec;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class SurfaceAreaCalculator {

    public SurfaceAreas compute(GeometrySpec g) {
        double l = g.room().length() / 1000;
        double w = g.room().width() / 1000;
        double h = g.room().height() / 1000;
        double perimeter = 2 * (l + w);
        double openings = g.openings().stream().mapToDouble(o -> o.width() * o.height() / 1e6).sum();
        double doorWidths = g.openings().stream().filter(o -> o.type() != OpeningType.WINDOW).mapToDouble(o -> o.width() / 1000).sum();
        // Feature wall = the longest wall without openings, else the longest wall.
        GeometrySpec.Wall feature = g.walls().stream()
                .sorted(Comparator.comparingDouble((GeometrySpec.Wall wl) -> g.openings().stream().anyMatch(o -> o.wallId().equals(wl.id())) ? 1 : 0)
                        .thenComparing(Comparator.comparingDouble(GeometrySpec.Wall::length).reversed()))
                .findFirst().orElseThrow();
        double featureArea = feature.length() / 1000 * h;
        return new SurfaceAreas(r(l * w), r(l * w), r(perimeter * h), r(openings), r(perimeter * h - openings),
                r(perimeter), r(perimeter - doorWidths), feature.side(), r(featureArea));
    }

    private static double r(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
