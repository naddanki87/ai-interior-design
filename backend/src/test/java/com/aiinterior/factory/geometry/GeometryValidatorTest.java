package com.aiinterior.factory.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterior.factory.domain.OpeningType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryValidatorTest {

    private final GeometryValidator validator = new GeometryValidator();

    private static List<String> codes(GeometryValidationReport r) {
        return r.issues().stream().map(ValidationIssue::code).toList();
    }

    @Test
    void emptyRoomIsValid() {
        GeometryValidationReport r = validator.validate(TestRooms.shell(List.of(), List.of()));
        assertThat(r.valid()).isTrue();
        assertThat(r.issues()).isEmpty();
    }

    @Test
    void detectsOutOfBoundsAndIntersections() {
        var spec = TestRooms.shell(List.of(), List.of(
                TestRooms.box("a", 3000, 2000, 1000, 1000, 700),
                TestRooms.box("b", 3500, 2500, 1000, 1000, 700),
                TestRooms.box("c", 4800, 3000, 1000, 500, 700)));
        var r = validator.validate(spec);
        assertThat(r.valid()).isFalse();
        assertThat(codes(r)).contains("INTERSECTION", "OUT_OF_BOUNDS");
    }

    @Test
    void doorSwingMustStayClear() {
        var spec = TestRooms.shell(List.of(), List.of(TestRooms.box("in-door", 400, 100, 600, 600, 700)));
        assertThat(codes(validator.validate(spec))).contains("BLOCKS_DOOR");
    }

    @Test
    void tallItemInFrontOfWindowIsWarnedLowItemIsNot() {
        var tall = TestRooms.shell(List.of(), List.of(TestRooms.box("tall", 4900, 1200, 300, 600, 2000)));
        var low = TestRooms.shell(List.of(), List.of(TestRooms.box("low", 4900, 1200, 300, 600, 500)));
        assertThat(codes(validator.validate(tall))).contains("BLOCKS_WINDOW");
        assertThat(validator.validate(low).issues()).isEmpty();
    }

    @Test
    void openingsMustFitTheirWallAndCeiling() {
        var base = TestRooms.shell(List.of(), List.of());
        List<GeometrySpec.Opening> ops = new ArrayList<>(base.openings());
        ops.add(new GeometrySpec.Opening("op-3", OpeningType.WINDOW, "wall-n", 4800, 900, 1200, 900, "Too far"));
        ops.add(new GeometrySpec.Opening("op-4", OpeningType.WINDOW, "wall-w", 100, 900, 2500, 900, "Too tall"));
        ops.add(new GeometrySpec.Opening("op-5", OpeningType.DOOR, "wall-x", 100, 900, 2000, 0, "Bad wall"));
        ops.add(new GeometrySpec.Opening("op-6", OpeningType.WINDOW, "wall-s", 800, 600, 1000, 900, "Overlaps door"));
        var spec = new GeometrySpec(base.schemaVersion(), base.sessionId(), 1, "mm", base.coordinateSystem(), base.room(), base.walls(),
                ops, List.of(), List.of(), List.of(), List.of(), "PENDING");
        assertThat(codes(validator.validate(spec))).contains("OPENING_OUT_OF_WALL", "OPENING_TOO_TALL", "INVALID_REFERENCE", "OPENINGS_OVERLAP");
    }

    @Test
    void duplicateIdsAndNonPositiveDimensionsAreErrors() {
        var spec = TestRooms.shell(List.of(), List.of(TestRooms.box("dup", 2000, 2000, 500, 500, 500),
                TestRooms.box("dup", 1000, 2500, 500, 500, 500), TestRooms.box("zero", 1000, 1000, 0, 500, 500)));
        assertThat(codes(validator.validate(spec))).contains("DUPLICATE_ID", "ITEM_DIMENSIONS");
    }

    @Test
    void invalidRoomIsRejectedNotCorrected() {
        var base = TestRooms.shell(List.of(), List.of());
        var spec = new GeometrySpec("1.0", null, 1, "mm", "", new GeometrySpec.Room(-5, 4000, 3000), base.walls(), List.of(),
                List.of(), List.of(), List.of(), List.of(), "PENDING");
        var r = validator.validate(spec);
        assertThat(r.valid()).isFalse();
        assertThat(codes(r)).containsExactly("ROOM_DIMENSIONS");
    }
}
