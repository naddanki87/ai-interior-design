package com.aiinterior.factory.materials;

/** One estimate line. All money values are indicative, never a vendor quotation. */
public record BoqLine(String code, String item, String specification, String unit, double quantity, double unitRate,
                      double materialAmount, double labour, double installation, double hardware, double wastagePct,
                      double wastageAmount, double lineTotal, String dataSource, String confidence, String assumption) {
}
