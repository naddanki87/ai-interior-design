package com.aiinterior.factory.materials;

import java.util.Locale;

public final class BoqCsvWriter {

    private BoqCsvWriter() {
    }

    public static String toCsv(String roomName, BoqEstimate boq) {
        StringBuilder sb = new StringBuilder();
        sb.append("Room,Code,Item,Specification,Unit,Quantity,Unit rate,Material,Labour,Installation,Hardware,Wastage %,Wastage,Line total,Data source,Confidence,Assumption\n");
        for (BoqLine l : boq.lines()) {
            sb.append(String.join(",", csv(roomName), csv(l.code()), csv(l.item()), csv(l.specification()), csv(l.unit()),
                    num(l.quantity()), num(l.unitRate()), num(l.materialAmount()), num(l.labour()), num(l.installation()),
                    num(l.hardware()), num(l.wastagePct()), num(l.wastageAmount()), num(l.lineTotal()), csv(l.dataSource()),
                    csv(l.confidence()), csv(l.assumption()))).append('\n');
        }
        sb.append(",,Subtotal,,,,,,,,,,,").append(num(boq.subtotal())).append('\n');
        sb.append(",,Transport (").append(num(boq.transportPct())).append("%),,,,,,,,,,,").append(num(boq.transport())).append('\n');
        sb.append(",,Contingency (").append(num(boq.contingencyPct())).append("%),,,,,,,,,,,").append(num(boq.contingency())).append('\n');
        sb.append(",,Tax (").append(num(boq.taxPct())).append("%),,,,,,,,,,,").append(num(boq.tax())).append('\n');
        sb.append(",,Grand total (").append(boq.currency()).append("),,,,,,,,,,,").append(num(boq.grandTotal())).append('\n');
        sb.append(",,").append(csv(boq.disclaimer())).append('\n');
        return sb.toString();
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Quotes values and neutralises spreadsheet formula injection. */
    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
