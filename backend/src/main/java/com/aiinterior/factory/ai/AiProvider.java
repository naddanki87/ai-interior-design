package com.aiinterior.factory.ai;

public interface AiProvider {

    String name();

    String model();

    /** Returns a JSON document for the request's purpose; throws {@link AiProviderException} on failure. */
    StructuredAiResult generateStructuredOutput(AiRequest request);
}
