package com.aiinterior.factory.design;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterior.factory.domain.RoomType;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.geometry.GeometryValidator;
import com.aiinterior.factory.geometry.Priority;
import com.aiinterior.factory.geometry.TestRooms;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LayoutPlannerTest {

    private final FurnitureCatalog catalog = new FurnitureCatalog();
    private final LayoutPlanner planner = new LayoutPlanner();
    private final GeometryValidator validator = new GeometryValidator();

    private static GeometrySpec with(GeometrySpec shell, List<GeometrySpec.Item> furniture) {
        return new GeometrySpec(shell.schemaVersion(), shell.sessionId(), 1, "mm", shell.coordinateSystem(), shell.room(), shell.walls(),
                shell.openings(), shell.fixedObjects(), furniture, List.of(), List.of(), "PENDING");
    }

    @Test
    void livingRoomAlternativesPlaceEverythingWithoutErrors() {
        GeometrySpec shell = TestRooms.shell(List.of(), List.of());
        var items = catalog.proposeFor(RoomType.LIVING_ROOM, List.of("3-seater sofa", "TV unit", "2 armchairs"));
        var alts = planner.alternatives(shell, items);
        assertThat(alts).hasSize(2);
        for (LayoutAlternative alt : alts) {
            var report = validator.validate(with(shell, alt.furniture()));
            assertThat(report.errorCount()).as(alt.id() + " " + report.issues()).isZero();
            assertThat(alt.unplaced()).as(alt.id()).noneMatch(u -> u.contains("REQUIRED"));
        }
    }

    @ParameterizedTest
    @EnumSource(RoomType.class)
    void everyRoomTypeProducesValidLayouts(RoomType type) {
        GeometrySpec shell = TestRooms.shell(List.of(), List.of());
        for (LayoutAlternative alt : planner.alternatives(shell, catalog.proposeFor(type, List.of()))) {
            assertThat(validator.validate(with(shell, alt.furniture())).errorCount()).as(type + " " + alt.id()).isZero();
        }
    }

    @Test
    void fixedItemsAreObstaclesAndNeverMoved() {
        var fixed = new GeometrySpec.Item("fix-1", "Existing cabinet", "FIXED", 2000, 3500, 1200, 600, 900, null, null, 0,
                Priority.MUST_NOT_CHANGE, "EXISTING_KEEP");
        GeometrySpec shell = TestRooms.shell(List.of(fixed), List.of());
        for (LayoutAlternative alt : planner.alternatives(shell, catalog.proposeFor(RoomType.LIVING_ROOM, List.of()))) {
            assertThat(alt.furniture()).noneMatch(f -> f.footprint().intersects(fixed.footprint()) && !"RUG".equals(f.category()));
            assertThat(validator.validate(with(shell, alt.furniture())).errorCount()).isZero();
        }
        assertThat(shell.fixedObjects()).containsExactly(fixed);
    }

    @Test
    void requestedItemsAreRequiredAndUnknownOnesAreFlaggedAsAssumedSize() {
        var items = catalog.proposeFor(RoomType.STUDY, List.of("2 x bookshelf", "piano"));
        assertThat(items.stream().filter(i -> i.key().startsWith("bookshelf"))).hasSize(2).allMatch(i -> i.priority() == Priority.REQUIRED);
        assertThat(items).anyMatch(i -> i.source().equals("CUSTOM_ASSUMED_SIZE") && i.name().equals("Piano"));
    }

    @Test
    void requestingADefaultPieceDoesNotDuplicateIt() {
        var items = catalog.proposeFor(RoomType.LIVING_ROOM, List.of("3-seater sofa"));
        assertThat(items.stream().filter(i -> i.key().startsWith("sofa"))).hasSize(1);
    }

    @Test
    void tooSmallRoomReportsUnplacedInsteadOfShrinkingFurniture() {
        var base = TestRooms.shell(List.of(), List.of());
        var tiny = new GeometrySpec(base.schemaVersion(), null, 1, "mm", base.coordinateSystem(), new GeometrySpec.Room(1800, 1500, 2700),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "PENDING");
        var alt = planner.alternatives(tiny, catalog.proposeFor(RoomType.MASTER_BEDROOM, List.of())).get(0);
        assertThat(alt.unplaced()).anyMatch(u -> u.startsWith("King bed"));
        assertThat(alt.furniture()).allMatch(f -> f.sizeX() > 0 && f.footprint().within(1800, 1500));
    }
}
