package com.aiinterior.factory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiinterior.factory.config.FactoryProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** No network: the HTTP layer is replaced by MockRestServiceServer. */
class HuggingFaceAiProviderTest {

    private static final FactoryProperties.HuggingFace CFG = new FactoryProperties.HuggingFace("hf_test_token",
            "https://router.example", "/v1/chat/completions", "org/some-model", true, 0.2, 500);
    private static final AiRequest REQ = new AiRequest(AiPurpose.REQUIREMENTS_INSIGHTS, "sys", "brief", Map.of());

    private MockRestServiceServer server;
    private HuggingFaceAiProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new HuggingFaceAiProvider(CFG, builder);
    }

    private static String completion(String content) {
        return """
                {"id":"x","object":"chat.completion","created":1,"model":"org/some-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
                """.formatted(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(content).toString());
    }

    @Test
    void sendsModelTokenAndJsonModeAndParsesUsage() {
        server.expect(requestTo("https://router.example/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer hf_test_token"))
                .andExpect(jsonPath("$.model").value("org/some-model"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(completion("```json\n{\"summary\":\"s\"}\n```"), MediaType.APPLICATION_JSON));
        StructuredAiResult r = provider.generateStructuredOutput(REQ);
        assertThat(r.json()).isEqualTo("{\"summary\":\"s\"}");
        assertThat(r.usage().totalTokens()).isEqualTo(18);
        server.verify();
    }

    private void expectFailure(HttpStatus status, String body, AiErrorCategory expected) {
        server.expect(requestTo("https://router.example/v1/chat/completions"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
        assertThatThrownBy(() -> provider.generateStructuredOutput(REQ))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.category()).isEqualTo(expected);
                    assertThat(e.getMessage()).doesNotContain("hf_test_token");
                });
    }

    @Test
    void mapsAuthentication() {
        expectFailure(HttpStatus.UNAUTHORIZED, "{\"error\":\"Invalid credentials\"}", AiErrorCategory.AUTHENTICATION);
    }

    @Test
    void mapsRateLimit() {
        expectFailure(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":\"rate limit\"}", AiErrorCategory.RATE_LIMITED);
    }

    @Test
    void mapsQuota() {
        expectFailure(HttpStatus.PAYMENT_REQUIRED, "{\"error\":\"credits exhausted\"}", AiErrorCategory.QUOTA_EXCEEDED);
    }

    @Test
    void mapsUnsupportedModel() {
        expectFailure(HttpStatus.BAD_REQUEST, "{\"error\":\"The requested model is not supported by any provider\"}", AiErrorCategory.UNSUPPORTED_MODEL);
    }

    @Test
    void mapsOutage() {
        expectFailure(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"down\"}", AiErrorCategory.PROVIDER_UNAVAILABLE);
    }

    @Test
    void proseWithoutJsonIsInvalidResponse() {
        server.expect(requestTo("https://router.example/v1/chat/completions"))
                .andRespond(withSuccess(completion("Sure! Here are some ideas."), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.generateStructuredOutput(REQ))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.category()).isEqualTo(AiErrorCategory.INVALID_RESPONSE));
    }

    @Test
    void refusesToStartWithoutTokenOrModel() {
        var noToken = new FactoryProperties.HuggingFace("", "u", "/p", "m", true, 0, 1);
        var noModel = new FactoryProperties.HuggingFace("t", "u", "/p", "", true, 0, 1);
        assertThatThrownBy(() -> new HuggingFaceAiProvider(noToken, RestClient.builder())).isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> new HuggingFaceAiProvider(noModel, RestClient.builder())).isInstanceOf(AiProviderException.class);
        assertThat(CFG.toString()).doesNotContain("hf_test_token");
    }
}
