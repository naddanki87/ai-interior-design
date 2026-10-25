package com.aiinterior.factory.geometry;

import java.util.List;

public record GeometryValidationReport(boolean valid, int errorCount, int warningCount, List<ValidationIssue> issues) {

    public static GeometryValidationReport of(List<ValidationIssue> issues) {
        long errors = issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR).count();
        return new GeometryValidationReport(errors == 0, (int) errors, issues.size() - (int) errors, List.copyOf(issues));
    }
}
