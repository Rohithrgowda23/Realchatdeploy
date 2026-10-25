package com.realchat.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Local, free, private: runs on your own machine with Ollama (https://ollama.com). Nothing leaves your computer. */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "ollama")
public class OllamaProvider extends HttpAiProvider {
    public OllamaProvider(@Value("${app.ai.base-url:http://localhost:11434}") String baseUrl, @Value("${app.ai.model:llama3.2}") String model,
                          @Value("${app.ai.timeout-seconds:30}") int timeoutSeconds, ObjectMapper mapper) {
        super(baseUrl, model, timeoutSeconds, mapper);
    }

    @Override
    public String complete(String system, String user) {
        Map<String, Object> body = Map.of("model", model, "stream", false, "options", Map.of("temperature", 0.2),
                "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        return finish(parse(post("/api/chat", body, Map.of()), "message", "content"));
    }

    @Override public String name() { return "ollama"; }
}