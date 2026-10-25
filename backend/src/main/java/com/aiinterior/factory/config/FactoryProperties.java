package com.aiinterior.factory.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of {@code factory.*} entries in application.properties.
 * Secrets (the Hugging Face token) are bound here and never returned by any endpoint.
 */
@ConfigurationProperties(prefix = "factory")
public record FactoryProperties(
        Ai ai,
        HuggingFace huggingface,
        Agents agents,
        Export export,
        SketchUp sketchup,
        List<String> corsAllowedOrigins) {

    public record Ai(String provider, Duration requestTimeout, int maxAttempts, Duration initialBackoff) {
    }

    public record HuggingFace(String token, String baseUrl, String completionsPath, String chatModel,
                              boolean jsonMode, double temperature, int maxTokens) {

        public boolean tokenConfigured() {
            return token != null && !token.isBlank();
        }

        @Override
        public String toString() {
            // Never let the token reach logs through toString().
            return "HuggingFace[baseUrl=" + baseUrl + ", chatModel=" + chatModel + ", token=" + (tokenConfigured() ? "***" : "<none>") + "]";
        }
    }

    public record Agents(Duration stepDelay, Duration peerRequestTimeout, int maxTaskAttempts, int maxSessions,
                         boolean requireApprovalBeforeExport, int maxRevisions) {
    }

    public record Export(String outputDir) {
    }

    public record SketchUp(String executable, String startupFlag, Duration timeout, boolean quitWhenDone) {

        public boolean configured() {
            return executable != null && !executable.isBlank();
        }
    }
}
