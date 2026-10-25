package com.aiinterior.factory.ai;

import java.util.Map;

/**
 * Text-only request. Agents send the minimum context needed (brief text, room type, style) -
 * never photographs, addresses or whole projects.
 */
public record AiRequest(AiPurpose purpose, String systemPrompt, String userPrompt, Map<String, Object> context) {
}
