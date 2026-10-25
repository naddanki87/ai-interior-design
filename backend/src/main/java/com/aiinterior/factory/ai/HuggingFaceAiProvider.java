package com.aiinterior.factory.ai;

import com.aiinterior.factory.config.FactoryProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Hugging Face Inference Providers via the OpenAI-compatible chat-completions router, using
 * Spring AI's {@link OpenAiChatModel}. Only chat completion is assumed; not every model supports
 * JSON mode, tools or vision, so JSON mode is a switch and the output is validated afterwards.
 * Retries are owned by {@link AiGateway}; Spring AI's own retry is disabled (1 attempt).
 */
public class HuggingFaceAiProvider implements AiProvider {

    private final FactoryProperties.HuggingFace cfg;
    private final OpenAiChatModel chatModel;

    /** {@code restClientBuilder} must already carry the HTTP request factory (timeouts) to use. */
    public HuggingFaceAiProvider(FactoryProperties.HuggingFace cfg, RestClient.Builder restClientBuilder) {
        if (!cfg.tokenConfigured()) {
            throw new AiProviderException(AiErrorCategory.NOT_CONFIGURED, "HF_TOKEN is not set");
        }
        if (cfg.chatModel() == null || cfg.chatModel().isBlank()) {
            throw new AiProviderException(AiErrorCategory.NOT_CONFIGURED, "HF_CHAT_MODEL / factory.huggingface.chat-model is not set");
        }
        this.cfg = cfg;
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(cfg.baseUrl())
                .completionsPath(cfg.completionsPath())
                .apiKey(cfg.token())
                .restClientBuilder(restClientBuilder)
                .responseErrorHandler(new HfErrorHandler())
                .build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(cfg.chatModel())
                .temperature(cfg.temperature())
                .maxTokens(cfg.maxTokens());
        if (cfg.jsonMode()) {
            options.responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null));
        }
        this.chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options.build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
    }

    /** Connect/read timeouts on a JDK HttpURLConnection factory (honours the JVM's proxy settings). */
    public static RestClient.Builder withTimeouts(RestClient.Builder builder, java.time.Duration timeout) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Math.min(timeout.toMillis(), 15_000));
        rf.setReadTimeout((int) timeout.toMillis());
        return builder.requestFactory(rf);
    }

    @Override
    public String name() {
        return "huggingface";
    }

    @Override
    public String model() {
        return cfg.chatModel();
    }

    @Override
    public StructuredAiResult generateStructuredOutput(AiRequest request) {
        long start = System.currentTimeMillis();
        String system = request.systemPrompt() + "\nRespond with ONE JSON object only, no prose, matching: "
                + AiInsights.schemaHint(request.purpose());
        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(List.of(new SystemMessage(system), new UserMessage(request.userPrompt()))));
        } catch (RuntimeException e) {
            throw map(e);
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new AiProviderException(AiErrorCategory.INVALID_RESPONSE, "Provider returned no choices");
        }
        String text = response.getResult().getOutput().getText();
        Usage u = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        AiUsage usage = u == null ? AiUsage.none() : new AiUsage(u.getPromptTokens(), u.getCompletionTokens(), u.getTotalTokens());
        return new StructuredAiResult(name(), model(), extractJson(text), usage, System.currentTimeMillis() - start);
    }

    /** Some models wrap JSON in markdown fences or add a sentence; keep only the outermost object. */
    static String extractJson(String text) {
        if (text == null) {
            throw new AiProviderException(AiErrorCategory.INVALID_RESPONSE, "Empty completion");
        }
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        if (s < 0 || e <= s) {
            throw new AiProviderException(AiErrorCategory.INVALID_RESPONSE, "Completion contained no JSON object");
        }
        return text.substring(s, e + 1);
    }

    private static AiProviderException map(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AiProviderException ape) {
                return ape;
            }
            if (t instanceof SocketTimeoutException) {
                return new AiProviderException(AiErrorCategory.TIMEOUT, "Hugging Face request timed out", e);
            }
        }
        if (e instanceof ResourceAccessException) {
            return new AiProviderException(AiErrorCategory.PROVIDER_UNAVAILABLE, "Cannot reach Hugging Face: " + e.getMessage(), e);
        }
        return new AiProviderException(AiErrorCategory.INVALID_RESPONSE, "Unexpected provider error: " + e.getMessage(), e);
    }

    /** Maps HTTP failures to categories. Response headers (and therefore credentials) are never echoed. */
    static final class HfErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
            handleError(response);
        }

        @Override
        public void handleError(ClientHttpResponse response) throws IOException {
            int status = response.getStatusCode().value();
            String body = readBody(response);
            String lower = body.toLowerCase(Locale.ROOT);
            AiErrorCategory cat;
            if (status == 401 || status == 403) {
                cat = AiErrorCategory.AUTHENTICATION;
            } else if (status == 402) {
                cat = AiErrorCategory.QUOTA_EXCEEDED;
            } else if (status == 429) {
                cat = AiErrorCategory.RATE_LIMITED;
            } else if (status == 404 || (status == 400 && lower.contains("model")
                    && (lower.contains("not supported") || lower.contains("not found") || lower.contains("does not exist")))) {
                cat = AiErrorCategory.UNSUPPORTED_MODEL;
            } else if (status >= 500) {
                cat = AiErrorCategory.PROVIDER_UNAVAILABLE;
            } else {
                cat = AiErrorCategory.INVALID_REQUEST;
            }
            throw new AiProviderException(cat, "Hugging Face HTTP " + status + ": " + body);
        }

        private static String readBody(ClientHttpResponse response) {
            try (InputStream in = response.getBody()) {
                byte[] b = in.readNBytes(500);
                return new String(b, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
            } catch (IOException e) {
                return "";
            }
        }
    }
}
