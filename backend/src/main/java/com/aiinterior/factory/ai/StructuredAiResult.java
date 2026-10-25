package com.aiinterior.factory.ai;

/** Raw provider output before validation. */
public record StructuredAiResult(String provider, String model, String json, AiUsage usage, long latencyMs) {
}
