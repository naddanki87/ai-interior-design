package com.aiinterior.factory.geometry;

public record ValidationIssue(Severity severity, String code, String elementId, String message) {

    public enum Severity { ERROR, WARNING }

    public static ValidationIssue error(String code, String id, String msg) {
        return new ValidationIssue(Severity.ERROR, code, id, msg);
    }

    public static ValidationIssue warning(String code, String id, String msg) {
        return new ValidationIssue(Severity.WARNING, code, id, msg);
    }
}
