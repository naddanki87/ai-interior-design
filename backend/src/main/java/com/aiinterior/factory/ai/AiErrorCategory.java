package com.aiinterior.factory.ai;

public enum AiErrorCategory {
    AUTHENTICATION(false),
    QUOTA_EXCEEDED(false),
    RATE_LIMITED(true),
    PROVIDER_UNAVAILABLE(true),
    TIMEOUT(true),
    INVALID_RESPONSE(true),
    UNSUPPORTED_MODEL(false),
    INVALID_REQUEST(false),
    NOT_CONFIGURED(false);

    private final boolean retryable;

    AiErrorCategory(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
