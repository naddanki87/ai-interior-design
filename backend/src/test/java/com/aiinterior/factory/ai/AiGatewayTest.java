package com.aiinterior.factory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterior.factory.config.FactoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AiGatewayTest {

    private static final FactoryProperties.Ai CFG = new FactoryProperties.Ai("test", Duration.ofSeconds(1), 3, Duration.ofMillis(1));
    private static final AiRequest REQ = new AiRequest(AiPurpose.REQUIREMENTS_INSIGHTS, "sys", "user", Map.of());
    private static final String GOOD = "{\"summary\":\"ok\",\"extractedConstraints\":[],\"conflicts\":[],\"assumptions\":[]}";

    /** Replays scripted outcomes: a String is returned as JSON, an exception is thrown. */
    static final class Scripted implements AiProvider {
        final Deque<Supplier<String>> script = new ArrayDeque<>();
        int calls;

        @Override
        public String name() {
            return "scripted";
        }

        @Override
        public String model() {
            return "m";
        }

        @Override
        public StructuredAiResult generateStructuredOutput(AiRequest request) {
            calls++;
            return new StructuredAiResult(name(), model(), script.pop().get(), AiUsage.none(), 1);
        }
    }

    private static Supplier<String> fail(AiErrorCategory c) {
        return () -> {
            throw new AiProviderException(c, c.name());
        };
    }

    private AiGateway gateway(Scripted p) {
        return new AiGateway(p, CFG, new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    void retriesRateLimitThenSucceeds() {
        Scripted p = new Scripted();
        p.script.add(fail(AiErrorCategory.RATE_LIMITED));
        p.script.add(() -> GOOD);
        var r = gateway(p).generate(REQ, AiInsights.RequirementsInsights.class);
        assertThat(r.value().summary()).isEqualTo("ok");
        assertThat(r.attempts()).isEqualTo(2);
    }

    @Test
    void authenticationFailureIsNotRetried() {
        Scripted p = new Scripted();
        p.script.add(fail(AiErrorCategory.AUTHENTICATION));
        p.script.add(() -> GOOD);
        assertThatThrownBy(() -> gateway(p).generate(REQ, AiInsights.RequirementsInsights.class))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.category()).isEqualTo(AiErrorCategory.AUTHENTICATION));
        assertThat(p.calls).isEqualTo(1);
    }

    @Test
    void retriesAreBounded() {
        Scripted p = new Scripted();
        for (int i = 0; i < 5; i++) {
            p.script.add(fail(AiErrorCategory.PROVIDER_UNAVAILABLE));
        }
        assertThatThrownBy(() -> gateway(p).generate(REQ, AiInsights.RequirementsInsights.class)).isInstanceOf(AiProviderException.class);
        assertThat(p.calls).isEqualTo(3);
    }

    @Test
    void malformedAndSchemaViolatingResponsesAreRejected() {
        Scripted p = new Scripted();
        p.script.add(() -> "not json");
        p.script.add(() -> "{\"summary\":\"\",\"extractedConstraints\":[],\"conflicts\":[],\"assumptions\":[]}");
        p.script.add(() -> "{\"summary\":\"x\"}");
        assertThatThrownBy(() -> gateway(p).generate(REQ, AiInsights.RequirementsInsights.class))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.category()).isEqualTo(AiErrorCategory.INVALID_RESPONSE));
        assertThat(p.calls).isEqualTo(3);
    }

    @Test
    void mockProviderSatisfiesEveryContract() {
        var gw = new AiGateway(new MockAiProvider(new ObjectMapper()), CFG, new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        for (AiPurpose purpose : AiPurpose.values()) {
            var r = gw.generate(new AiRequest(purpose, "s", "u", Map.of("style", "Japandi", "roomType", "Study",
                    "instructions", "Must keep the window clear. Need lots of storage")), AiInsights.responseType(purpose));
            assertThat(r.value()).isNotNull();
            assertThat(r.provider()).isEqualTo("mock");
        }
    }
}
