package com.aiinterior.factory.ai;

public record AiUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    public static AiUsage none() {
        return new AiUsage(null, null, null);
    }
}
