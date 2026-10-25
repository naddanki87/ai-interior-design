package com.aiinterior.factory.ai;

public class AiProviderException extends RuntimeException {

    private final AiErrorCategory category;

    public AiProviderException(AiErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    public AiProviderException(AiErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public AiErrorCategory category() {
        return category;
    }
}
