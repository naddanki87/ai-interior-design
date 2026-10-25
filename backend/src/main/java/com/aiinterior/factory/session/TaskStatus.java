package com.aiinterior.factory.session;

public enum TaskStatus {
    PENDING, READY, RUNNING, WAITING_FOR_INPUT, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED, CANCELLED, SKIPPED;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == SKIPPED;
    }
}
