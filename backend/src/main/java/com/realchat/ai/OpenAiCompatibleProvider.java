package com.realchat.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Any service that speaks the OpenAI chat-completions format (several offer free tiers; check their current terms).
 * The API key lives only in the backend environment (AI_API_KEY) and is sent only to the configured base URL.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "openai")
public class OpenAiCompatibleProvider extends HttpAiProvider {
    private final String apiKey;

    public OpenAiCompatibleProvider(@Value("${app.ai.base-url}") String baseUrl, @Value("${app.ai.model}") String model,
                                    @Value("${app.ai.api-key:}") String apiKey, @Value("${app.ai.timeout-seconds:30}") int timeoutSeconds, ObjectMapper mapper) {
        super(baseUrl, model, timeoutSeconds, mapper);
        this.apiKey = apiKey;
    }

    @Override
    public String complete(String system, String user) {
        Map<String, Object> body = Map.of("model", model, "temperature", 0.2, "max_tokens", 800,
                "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        Map<String, String> headers = apiKey == null || apiKey.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey);
        return finish(parse(post("/chat/completions", body, headers), "choices", "0", "message", "content"));
    }

    @Override public String name() { return "openai-compatible"; }
}