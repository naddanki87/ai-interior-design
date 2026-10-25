package com.aiinterior.factory.session;

public enum SessionStatus {
    RUNNING, WAITING_FOR_INPUT, WAITING_FOR_APPROVAL, COMPLETED, COMPLETED_WITH_WARNINGS, FAILED, CANCELLED;

    public boolean finished() {
        return this == COMPLETED || this == COMPLETED_WITH_WARNINGS || this == FAILED || this == CANCELLED
                || this == WAITING_FOR_INPUT;
    }
}
