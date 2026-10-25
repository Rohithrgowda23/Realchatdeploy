package com.realchat.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realchat.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Shared HTTP plumbing. The base URL comes only from server configuration, never from a user. Bodies are never logged. */
abstract class HttpAiProvider implements AiProvider {
    private static final Logger log = LoggerFactory.getLogger(HttpAiProvider.class);
    static final int MAX_OUTPUT_CHARS = 6000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    protected final ObjectMapper mapper;
    protected final String baseUrl;
    protected final String model;
    private final Duration timeout;

    HttpAiProvider(String baseUrl, String model, int timeoutSeconds, ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", ""); this.model = model; this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds)); this.mapper = mapper;
    }

    protected String post(String path, Object body, Map<String, String> headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout).header("Content-Type", "application/json");
            headers.forEach(b::header);
            HttpResponse<String> res = http.send(b.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                log.warn("AI provider answered HTTP {}", res.statusCode());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI provider returned an error. Please try again later.");
            }
            return res.body();
        } catch (HttpTimeoutException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "The AI provider took too long to answer");
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not build the AI request");
        } catch (IOException e) {
            log.warn("AI provider unreachable: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI provider is not reachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI request was interrupted");
        }
    }

    protected String finish(String text) {
        if (text == null || text.isBlank()) throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI provider returned an empty answer");
        String t = text.strip();
        return t.length() > MAX_OUTPUT_CHARS ? t.substring(0, MAX_OUTPUT_CHARS) : t;
    }

    protected String parse(String json, String... path) {
        try {
            var node = mapper.readTree(json);
            for (String p : path) node = p.matches("\\d+") ? node.path(Integer.parseInt(p)) : node.path(p);
            return node.asText("");
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI provider sent an unreadable answer");
        }
    }

    public boolean available() { return true; }
    public String model() { return model; }
}