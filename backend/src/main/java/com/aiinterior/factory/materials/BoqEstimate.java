package com.aiinterior.factory.materials;

import java.util.List;

public record BoqEstimate(String currency, List<BoqLine> lines, double subtotal, double transportPct, double transport,
                          double contingencyPct, double contingency, double taxPct, double tax, double grandTotal,
                          Double budget, String budgetStatus, String disclaimer, List<String> assumptions) {
}
