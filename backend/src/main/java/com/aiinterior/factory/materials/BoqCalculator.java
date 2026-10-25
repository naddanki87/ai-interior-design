package com.aiinterior.factory.materials;

import com.aiinterior.factory.domain.Budget;
import com.aiinterior.factory.domain.WallSide;
import com.aiinterior.factory.geometry.GeometrySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Indicative estimate from a small built-in INR rate table (typical Indian metro market ranges).
 * Quantities come from geometry; rates are assumptions and are labelled as such on every line.
 */
@Component
public class BoqCalculator {

    public static final String SOURCE = "Built-in indicative rate table (INR, unverified)";
    public static final String DISCLAIMER = "INDICATIVE ESTIMATE - not a vendor quotation. Rates are typical market "
            + "assumptions; obtain verified quotations before committing.";

    private static final double TRANSPORT_PCT = 2;
    private static final double CONTINGENCY_PCT = 7;
    private static final double TAX_PCT = 18;

    /** Loose furniture allowances (supply only). */
    private static final Map<String, Double> ALLOWANCE = Map.ofEntries(
            Map.entry("SEATING", 45000.0), Map.entry("BED", 55000.0), Map.entry("TABLE", 18000.0),
            Map.entry("DESK", 22000.0), Map.entry("APPLIANCE", 0.0), Map.entry("SANITARY", 18000.0),
            Map.entry("DECOR", 3500.0), Map.entry("RUG", 15000.0), Map.entry("CUSTOM", 15000.0));

    public BoqEstimate estimate(GeometrySpec g, SurfaceAreas a, int downlights, double coveMetres, Budget budget) {
        List<BoqLine> lines = new ArrayList<>();
        lines.add(line("FL-01", "Flooring", "Vitrified tile 600x1200, matt, incl. adhesive & grout", "m²", a.floorM2(), 900, 250, 0, 0, 8,
                "HIGH", "Tile size and pattern affect wastage; 8% assumed"));
        lines.add(line("FL-02", "Skirting", "Tile skirting 100 mm", "rm", a.skirtingM(), 180, 60, 0, 0, 8,
                "HIGH", "Door widths deducted"));
        lines.add(line("WL-01", "Wall painting", "Putty, primer, 2 coats washable emulsion", "m²", a.wallNetM2(), 110, 90, 0, 0, 5,
                "HIGH", "Openings deducted"));
        lines.add(line("WL-02", "Feature wall", "Fluted veneer/WPC panelling on " + a.featureWall() + " wall", "m²", a.featureWallM2(), 2400, 0, 600, 0, 10,
                "MEDIUM", "Full wall height assumed"));
        lines.add(line("CL-01", "Ceiling painting", "2 coats ceiling white", "m²", a.ceilingM2(), 90, 90, 0, 0, 5, "HIGH", null));
        if (coveMetres > 0) {
            lines.add(line("CL-02", "False ceiling with cove", "Gypsum perimeter band + cove", "rm", coveMetres, 1100, 0, 350, 0, 5,
                    "LOW", "Feasibility depends on ceiling construction and services - not yet confirmed"));
        }
        for (GeometrySpec.Item it : g.furniture()) {
            String cat = it.category();
            if (cat.equals("STORAGE") || cat.equals("KITCHEN") || cat.equals("POOJA")) {
                boolean sideWall = it.againstWall() == WallSide.EAST || it.againstWall() == WallSide.WEST;
                double front = round((sideWall ? it.sizeY() : it.sizeX()) * it.height() / 1e6);
                String spec = cat.equals("KITCHEN") ? "BWP ply carcass, acrylic/laminate shutters, quartz top where applicable"
                        : "BWR ply carcass, laminate finish, soft-close hardware";
                lines.add(line("FU-" + it.id(), it.name(), spec, "m² (front)", front, cat.equals("KITCHEN") ? 18000 : 14000, 0, 1000, 1500, 3,
                        "MEDIUM", "Custom carpentry rate per m² of front area"));
            } else {
                double allowance = ALLOWANCE.getOrDefault(cat, 15000.0);
                if (allowance > 0) {
                    lines.add(line("LF-" + it.id(), it.name(), "Loose furniture allowance (supply)", "no.", 1, allowance, 0, 0, 0, 0,
                            "LOW", "Allowance only - depends entirely on the product you choose"));
                }
            }
        }
        if (downlights > 0) {
            lines.add(line("LT-01", "LED downlights", "12 W, CRI>90, anti-glare", "no.", downlights, 900, 0, 250, 0, 0, "MEDIUM",
                    "Wiring and switch points not included"));
        }
        if (coveMetres > 0) {
            lines.add(line("LT-02", "Cove LED strip", "24 V COB strip + profile + driver", "rm", coveMetres, 450, 0, 120, 0, 5, "MEDIUM", null));
        }
        double subtotal = round(lines.stream().mapToDouble(BoqLine::lineTotal).sum());
        double transport = round(subtotal * TRANSPORT_PCT / 100);
        double contingency = round(subtotal * CONTINGENCY_PCT / 100);
        double tax = round((subtotal + transport) * TAX_PCT / 100);
        double total = round(subtotal + transport + contingency + tax);

        Double budgetAmount = budget == null ? null : budget.amount();
        String currency = budget == null || budget.currency() == null || budget.currency().isBlank() ? "INR" : budget.currency().toUpperCase(Locale.ROOT);
        String status;
        if (budgetAmount == null || budgetAmount <= 0) {
            status = "NO_BUDGET_GIVEN";
        } else if (!currency.equals("INR")) {
            status = "NOT_COMPARED - budget is in " + currency + ", rates are INR and no conversion is applied";
        } else {
            status = total <= budgetAmount ? "WITHIN_BUDGET" : String.format(Locale.ROOT, "OVER_BUDGET by %.0f INR", total - budgetAmount);
        }
        return new BoqEstimate("INR", lines, subtotal, TRANSPORT_PCT, transport, CONTINGENCY_PCT, contingency, TAX_PCT, tax,
                total, budgetAmount, status, DISCLAIMER, List.of(
                "GST assumed at 18% on all lines - actual rates differ by item and contract type",
                "Electrical, plumbing, civil and demolition work are excluded",
                "Rates: " + SOURCE));
    }

    private static BoqLine line(String code, String item, String spec, String unit, double qty, double rate, double labourRate,
                                double installRate, double hardwareRate, double wastagePct, String confidence, String assumption) {
        double material = round(qty * rate);
        double labour = round(qty * labourRate);
        double install = round(qty * installRate);
        double hardware = round(qty * hardwareRate);
        double wastage = round(material * wastagePct / 100);
        double total = round(material + labour + install + hardware + wastage);
        return new BoqLine(code, item, spec, unit, round(qty), rate, material, labour, install, hardware, wastagePct, wastage,
                total, SOURCE, confidence, assumption);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
