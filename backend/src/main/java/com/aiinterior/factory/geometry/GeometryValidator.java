package com.aiinterior.factory.geometry;

import com.aiinterior.factory.domain.OpeningType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Independent checks on a {@link GeometrySpec}. Reports problems; never "fixes" dimensions. */
@Component
public class GeometryValidator {

    public GeometryValidationReport validate(GeometrySpec spec) {
        List<ValidationIssue> issues = new ArrayList<>();
        GeometrySpec.Room room = spec.room();
        if (!GeometrySpec.SCHEMA_VERSION.equals(spec.schemaVersion())) {
            issues.add(ValidationIssue.error("SCHEMA_VERSION", null, "Unsupported schema version " + spec.schemaVersion()));
        }
        if (room == null || room.length() <= 0 || room.width() <= 0 || room.height() <= 0) {
            issues.add(ValidationIssue.error("ROOM_DIMENSIONS", "room", "Room length, width and height must all be positive"));
            return GeometryValidationReport.of(issues);
        }
        if (room.height() < 2100 || room.height() > 6000) {
            issues.add(ValidationIssue.warning("CEILING_HEIGHT", "room", "Ceiling height " + room.height() + " mm is unusual - please re-check"));
        }

        Set<String> ids = new HashSet<>();
        Stream.of(spec.walls().stream().map(GeometrySpec.Wall::id), spec.openings().stream().map(GeometrySpec.Opening::id),
                        spec.fixedObjects().stream().map(GeometrySpec.Item::id), spec.furniture().stream().map(GeometrySpec.Item::id))
                .flatMap(Function.identity())
                .forEach(id -> {
                    if (!ids.add(id)) {
                        issues.add(ValidationIssue.error("DUPLICATE_ID", id, "Duplicate element id " + id));
                    }
                });

        Map<String, GeometrySpec.Wall> walls = spec.walls().stream().collect(Collectors.toMap(GeometrySpec.Wall::id, w -> w, (a, b) -> a));
        for (GeometrySpec.Opening o : spec.openings()) {
            GeometrySpec.Wall wall = walls.get(o.wallId());
            if (wall == null) {
                issues.add(ValidationIssue.error("INVALID_REFERENCE", o.id(), o.label() + " references unknown wall " + o.wallId()));
                continue;
            }
            if (o.width() <= 0 || o.height() <= 0) {
                issues.add(ValidationIssue.error("OPENING_DIMENSIONS", o.id(), o.label() + " must have positive width and height"));
            }
            if (o.offset() < 0 || o.offset() + o.width() > wall.length() + 0.5) {
                issues.add(ValidationIssue.error("OPENING_OUT_OF_WALL", o.id(), String.format(
                        "%s spans %.0f-%.0f mm but the %s wall is only %.0f mm long", o.label(), o.offset(), o.offset() + o.width(), wall.side(), wall.length())));
            }
            if (o.sillHeight() < 0 || o.sillHeight() + o.height() > room.height() + 0.5) {
                issues.add(ValidationIssue.error("OPENING_TOO_TALL", o.id(), String.format(
                        "%s top at %.0f mm exceeds ceiling height %.0f mm", o.label(), o.sillHeight() + o.height(), room.height())));
            }
            if (o.type() == OpeningType.DOOR && o.sillHeight() > 0) {
                issues.add(ValidationIssue.warning("DOOR_SILL", o.id(), o.label() + " has a raised sill of " + o.sillHeight() + " mm"));
            }
        }
        for (int i = 0; i < spec.openings().size(); i++) {
            for (int j = i + 1; j < spec.openings().size(); j++) {
                GeometrySpec.Opening a = spec.openings().get(i);
                GeometrySpec.Opening b = spec.openings().get(j);
                if (a.wallId().equals(b.wallId()) && a.offset() < b.offset() + b.width() && b.offset() < a.offset() + a.width()) {
                    issues.add(ValidationIssue.error("OPENINGS_OVERLAP", a.id(), a.label() + " overlaps " + b.label() + " on the same wall"));
                }
            }
        }

        List<GeometrySpec.Item> all = new ArrayList<>(spec.fixedObjects());
        all.addAll(spec.furniture());
        for (GeometrySpec.Item it : all) {
            if (it.sizeX() <= 0 || it.sizeY() <= 0 || it.height() <= 0) {
                issues.add(ValidationIssue.error("ITEM_DIMENSIONS", it.id(), it.name() + " must have positive dimensions"));
            }
            if (!it.footprint().within(room.length(), room.width())) {
                issues.add(ValidationIssue.error("OUT_OF_BOUNDS", it.id(), it.name() + " extends outside the room"));
            }
            if (it.height() > room.height()) {
                issues.add(ValidationIssue.error("ITEM_TOO_TALL", it.id(), it.name() + " is taller than the ceiling"));
            }
        }
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                GeometrySpec.Item a = all.get(i);
                GeometrySpec.Item b = all.get(j);
                if (a.footprint().intersects(b.footprint()) && !stackable(a, b)) {
                    issues.add(ValidationIssue.error("INTERSECTION", a.id(), a.name() + " intersects " + b.name()));
                }
            }
        }
        for (GeometrySpec.Item a : spec.furniture()) {
            Rect zone = a.clearanceZone();
            if (zone == null) {
                continue;
            }
            for (GeometrySpec.Item b : all) {
                if (b != a && !stackable(a, b) && zone.intersects(b.footprint())) {
                    issues.add(ValidationIssue.warning("CLEARANCE", a.id(), String.format(
                            "%s needs %.0f mm free in front but %s is in the way", a.name(), a.frontClearance(), b.name())));
                }
            }
        }
        for (KeepOutZones.Zone z : KeepOutZones.of(spec)) {
            for (GeometrySpec.Item it : all) {
                if (it.footprint().intersects(z.rect()) && it.height() > z.maxItemHeight() && !"RUG".equals(it.category())) {
                    String code = z.type() == OpeningType.WINDOW ? "BLOCKS_WINDOW" : "BLOCKS_DOOR";
                    var issue = z.type() == OpeningType.WINDOW
                            ? ValidationIssue.warning(code, it.id(), it.name() + " stands in front of window " + z.openingId())
                            : ValidationIssue.error(code, it.id(), it.name() + " blocks the swing/passage of " + z.openingId());
                    issues.add(issue);
                }
            }
        }
        return GeometryValidationReport.of(issues);
    }

    /** Rugs sit under other furniture by design. */
    private static boolean stackable(GeometrySpec.Item a, GeometrySpec.Item b) {
        return "RUG".equals(a.category()) || "RUG".equals(b.category());
    }
}
