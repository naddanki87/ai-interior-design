package com.aiinterior.factory.agents.impl;

import com.aiinterior.factory.agents.Agent;
import com.aiinterior.factory.agents.AgentContext;
import com.aiinterior.factory.agents.AgentId;
import com.aiinterior.factory.agents.AgentReply;
import com.aiinterior.factory.agents.AgentTool;
import com.aiinterior.factory.agents.Payloads.MeasurementCheck;
import com.aiinterior.factory.agents.Payloads.RoomSpec;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.domain.Dimension;
import com.aiinterior.factory.domain.DimensionSource;
import com.aiinterior.factory.domain.KeepItemInput;
import com.aiinterior.factory.domain.LengthInput;
import com.aiinterior.factory.domain.OpeningInput;
import com.aiinterior.factory.domain.OpeningType;
import com.aiinterior.factory.domain.WallSide;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.geometry.Priority;
import com.aiinterior.factory.units.LengthParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds the room shell from the user's measurements and lists exactly what is still missing. */
@Component
public class RoomPlanningAgent implements Agent {

    public static final String BUILD_ROOM_SPEC = "BUILD_ROOM_SPEC";
    public static final double WALL_THICKNESS = 115;

    @Override
    public AgentId id() {
        return AgentId.ROOM_PLANNING;
    }

    @Override
    public String role() {
        return "Turns measurements, doors, windows and fixed items into a room specification and lists the minimum missing measurements.";
    }

    @Override
    public Set<AgentTool> tools() {
        return Set.of();
    }

    @Override
    public Set<String> intents() {
        return Set.of(BUILD_ROOM_SPEC);
    }

    @Override
    public AgentReply handle(String intent, Object payload, AgentContext ctx) {
        DesignBrief b = (DesignBrief) payload;
        List<MeasurementCheck> checks = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<GeometrySpec.MeasurementSource> sources = new ArrayList<>();

        Dimension l = measure("Room length", b.length(), true, checks, missing, warnings, sources);
        Dimension w = measure("Room width", b.width(), true, checks, missing, warnings, sources);
        Dimension h = measure("Ceiling height", b.height(), true, checks, missing, warnings, sources);
        sources.add(new GeometrySpec.MeasurementSource("Wall thickness (modelling only)", WALL_THICKNESS, DimensionSource.USER_ASSUMPTION,
                "default 115 mm; does not affect interior dimensions"));

        List<GeometrySpec.Wall> walls = new ArrayList<>();
        List<GeometrySpec.Opening> openings = new ArrayList<>();
        List<GeometrySpec.Item> fixed = new ArrayList<>();
        if (l.known() && w.known() && h.known()) {
            double L = l.value();
            double W = w.value();
            double H = h.value();
            walls.add(new GeometrySpec.Wall("wall-s", WallSide.SOUTH, 0, 0, L, 0, WALL_THICKNESS, H));
            walls.add(new GeometrySpec.Wall("wall-e", WallSide.EAST, L, 0, L, W, WALL_THICKNESS, H));
            walls.add(new GeometrySpec.Wall("wall-n", WallSide.NORTH, L, W, 0, W, WALL_THICKNESS, H));
            walls.add(new GeometrySpec.Wall("wall-w", WallSide.WEST, 0, W, 0, 0, WALL_THICKNESS, H));
        }
        int doors = 0;
        int windows = 0;
        int n = 0;
        for (OpeningInput o : b.openingsOrEmpty()) {
            n++;
            String label = o.label() != null && !o.label().isBlank() ? o.label()
                    : o.type() == OpeningType.WINDOW ? "Window " + (++windows) : (o.type() == OpeningType.DOOR ? "Door " : "Opening ") + (++doors);
            Dimension off = measure(label + " offset on " + o.wall() + " wall", o.offset(), true, checks, missing, warnings, sources);
            Dimension ow = measure(label + " width", o.width(), true, checks, missing, warnings, sources);
            Dimension oh = measure(label + " height", o.height(), true, checks, missing, warnings, sources);
            Dimension sill;
            if (o.type() == OpeningType.WINDOW) {
                sill = measure(label + " sill height", o.sillHeight(), true, checks, missing, warnings, sources);
            } else if (o.sillHeight() == null || o.sillHeight().blank()) {
                sill = new Dimension(0.0, DimensionSource.USER_ASSUMPTION, "door/opening at floor level");
                sources.add(new GeometrySpec.MeasurementSource(label + " sill height", 0.0, DimensionSource.USER_ASSUMPTION, "assumed at floor level"));
            } else {
                sill = measure(label + " sill height", o.sillHeight(), false, checks, missing, warnings, sources);
            }
            if (off.known() && ow.known() && oh.known() && sill.known() && !walls.isEmpty()) {
                openings.add(new GeometrySpec.Opening("op-" + n, o.type(), "wall-" + o.wall().name().substring(0, 1).toLowerCase(Locale.ROOT),
                        off.value(), ow.value(), oh.value(), sill.value(), label));
            }
        }
        if (b.openingsOrEmpty().stream().noneMatch(o -> o.type() != OpeningType.WINDOW)) {
            warnings.add("No door or passage given - door swing and circulation cannot be checked");
        }
        int k = 0;
        for (KeepItemInput item : b.keepItemsOrEmpty()) {
            k++;
            String name = item.name().trim();
            Dimension iw = measure(name + " width", item.width(), true, checks, missing, warnings, sources);
            Dimension id = measure(name + " depth", item.depth(), true, checks, missing, warnings, sources);
            Dimension ih = measure(name + " height", item.height(), true, checks, missing, warnings, sources);
            Dimension ix = measure(name + " position X", item.x(), true, checks, missing, warnings, sources);
            Dimension iy = measure(name + " position Y", item.y(), true, checks, missing, warnings, sources);
            if (iw.known() && id.known() && ih.known() && ix.known() && iy.known()) {
                fixed.add(new GeometrySpec.Item("fix-" + k, name, "FIXED", ix.value(), iy.value(), iw.value(), id.value(), ih.value(),
                        null, null, 0, Priority.MUST_NOT_CHANGE, "EXISTING_KEEP"));
            }
        }

        boolean blocking = !missing.isEmpty();
        GeometrySpec shell = blocking ? null : new GeometrySpec(GeometrySpec.SCHEMA_VERSION, null, 0, "mm", GeometrySpec.COORDINATES,
                new GeometrySpec.Room(l.value(), w.value(), h.value()), walls, openings, fixed, List.of(),
                List.of("Structural elements are fixed; furniture must keep door swings and passages clear"), sources, "PENDING");
        double area = l.known() && w.known() ? Math.round(l.value() * w.value() / 1e4) / 100.0 : 0;
        RoomSpec spec = new RoomSpec(shell, checks, missing, warnings, blocking, area);
        String summary = blocking
                ? "Cannot model yet - " + missing.size() + " measurement(s) missing: " + String.join("; ", missing)
                : String.format(Locale.ROOT, "Room %.0f x %.0f x %.0f mm (%.2f m²), %d opening(s), %d fixed item(s), %d warning(s)",
                l.value(), w.value(), h.value(), area, openings.size(), fixed.size(), warnings.size());
        return new AgentReply(summary, spec);
    }

    private static Dimension measure(String field, LengthInput in, boolean mandatory, List<MeasurementCheck> checks,
                                     List<String> missing, List<String> warnings, List<GeometrySpec.MeasurementSource> sources) {
        Dimension d;
        try {
            d = LengthParser.toDimension(in);
        } catch (IllegalArgumentException e) {
            checks.add(new MeasurementCheck(field, null, DimensionSource.UNKNOWN, "INVALID", e.getMessage()));
            missing.add(field + " (invalid: " + e.getMessage() + ")");
            return Dimension.unknown();
        }
        if (!d.known()) {
            checks.add(new MeasurementCheck(field, null, DimensionSource.UNKNOWN, "MISSING", mandatory ? "Required" : "Optional"));
            if (mandatory) {
                missing.add(field);
            }
            return d;
        }
        String status = d.source().verified() ? "OK" : "UNVERIFIED";
        if (!d.source().verified()) {
            warnings.add(field + " is " + d.source() + " - please measure to confirm");
        }
        checks.add(new MeasurementCheck(field, d.mm(), d.source(), status, d.originalInput()));
        sources.add(new GeometrySpec.MeasurementSource(field, d.mm(), d.source(), d.originalInput()));
        return d;
    }
}
