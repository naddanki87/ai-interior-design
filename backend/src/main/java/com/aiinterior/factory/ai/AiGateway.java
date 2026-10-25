package com.aiinterior.factory.ai;

import com.aiinterior.factory.config.FactoryProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Single entry point for agents: picks the configured provider, retries transient failures a
 * bounded number of times with exponential backoff, and validates every response against its
 * typed contract before an agent sees it.
 */
@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    public record TypedAiResult<T>(T value, String provider, String model, AiUsage usage, int attempts, long latencyMs) {
    }

    private final AiProvider provider;
    private final FactoryProperties.Ai cfg;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final String configurationError;

    @Autowired
    public AiGateway(FactoryProperties props, ObjectMapper mapper, Validator validator, RestClient.Builder restClientBuilder) {
        this.cfg = props.ai();
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.validator = validator;
        AiProvider p;
        String err = null;
        if ("huggingface".equalsIgnoreCase(cfg.provider())) {
            try {
                p = new HuggingFaceAiProvider(props.huggingface(),
                        HuggingFaceAiProvider.withTimeouts(restClientBuilder.clone(), cfg.requestTimeout()));
            } catch (AiProviderException e) {
                err = e.getMessage();
                p = null;
                log.warn("Hugging Face provider not usable: {}", err);
            }
        } else {
            p = new MockAiProvider(mapper);
        }
        this.provider = p;
        this.configurationError = err;
    }

    /** Visible for tests. */
    public AiGateway(AiProvider provider, FactoryProperties.Ai cfg, ObjectMapper mapper, Validator validator) {
        this.provider = provider;
        this.cfg = cfg;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.validator = validator;
        this.configurationError = null;
    }

    public String providerName() {
        return provider == null ? cfg.provider() + " (not configured)" : provider.name();
    }

    public String modelName() {
        return provider == null ? "-" : provider.model();
    }

    public String configurationError() {
        return configurationError;
    }

    public <T> TypedAiResult<T> generate(AiRequest request, Class<T> type) {
        if (provider == null) {
            throw new AiProviderException(AiErrorCategory.NOT_CONFIGURED, configurationError);
        }
        int max = Math.max(1, cfg.maxAttempts());
        long backoff = cfg.initialBackoff().toMillis();
        AiProviderException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                StructuredAiResult raw = provider.generateStructuredOutput(request);
                T value = parse(raw.json(), type);
                return new TypedAiResult<>(value, raw.provider(), raw.model(), raw.usage(), attempt, raw.latencyMs());
            } catch (AiProviderException e) {
                last = e;
                log.warn("AI {} attempt {}/{} failed: {} {}", request.purpose(), attempt, max, e.category(), e.getMessage());
                if (!e.category().retryable() || attempt == max) {
                    break;
                }
                sleep(backoff);
                backoff = Math.min(backoff * 2, 15_000);
            }
        }
        throw last;
    }

    private <T> T parse(String json, Class<T> type) {
        T value;
        try {
            value = mapper.readValue(json, type);
        } catch (Exception e) {
            throw new AiProviderException(AiErrorCategory.INVALID_RESPONSE, "Response is not valid " + type.getSimpleName() + " JSON: " + e.getMessage());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new AiProviderException(AiErrorCategory.INVALID_RESPONSE, "Response failed validation: "
                    + violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining("; ")));
        }
        return value;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiErrorCategory.TIMEOUT, "Interrupted while backing off");
        }
    }
}
