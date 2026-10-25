
package com.realchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realchat.ai.OllamaProvider;
import com.realchat.ai.OpenAiCompatibleProvider;
import com.realchat.exception.ApiException;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests AI providers using a local HTTP server.
 * No external AI service is required.
 */
class AiProviderHttpTest {

    private HttpServer server;
    private String base;

    private final AtomicReference<String> lastBody =
            new AtomicReference<>();

    private final AtomicReference<String> lastAuth =
            new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void respond(
            String path,
            int status,
            String body,
            long delayMs
    ) {
        server.createContext(path, exchange -> {
            try {
                lastBody.set(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                ));

                lastAuth.set(
                        exchange.getRequestHeaders()
                                .getFirst("Authorization")
                );

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                byte[] response = body.getBytes(StandardCharsets.UTF_8);

                try {
                    exchange.sendResponseHeaders(status, response.length);
                    exchange.getResponseBody().write(response);
                } catch (IOException ignored) {
                    // The client may have timed out or disconnected.
                }
            } finally {
                exchange.close();
            }
        });

        server.start();
    }

    @Test
    void ollamaReturnsTheAssistantText() {
        respond(
                "/api/chat",
                200,
                "{\"message\":{\"role\":\"assistant\",\"content\":\" hola mundo \"}}",
                0
        );

        OllamaProvider provider = new OllamaProvider(
                base, "llama3.2", 5, new ObjectMapper()
        );

        assertEquals("hola mundo",
                provider.complete("translate", "hello world"));

        assertTrue(lastBody.get().contains("llama3.2"));
        assertTrue(lastBody.get().contains("hello world"));
        assertTrue(lastBody.get().contains("\"stream\":false"));

        assertEquals("ollama", provider.name());
        assertTrue(provider.available());
    }

    @Test
    void openAiCompatibleSendsTheKeyOnlyAsBearerHeader() {
        respond(
                "/v1/chat/completions",
                200,
                "{\"choices\":[{\"message\":{\"content\":\"bonjour\"}}]}",
                0
        );

        OpenAiCompatibleProvider provider =
                new OpenAiCompatibleProvider(
                        base + "/v1/",
                        "some-model",
                        "sk-test",
                        5,
                        new ObjectMapper()
                );

        assertEquals("bonjour",
                provider.complete("translate", "hello"));

        assertEquals("Bearer sk-test", lastAuth.get());
        assertFalse(lastBody.get().contains("sk-test"));
    }

    @Test
    void providerErrorBecomes502() {
        respond(
                "/api/chat",
                500,
                "{\"error\":\"boom with details\"}",
                0
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> new OllamaProvider(
                        base, "m", 5, new ObjectMapper()
                ).complete("s", "u")
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertFalse(exception.getMessage().contains("boom"));
    }

    @Test
    void slowProviderBecomes504() {
        respond(
                "/api/chat",
                200,
                "{\"message\":{\"content\":\"late\"}}",
                2500
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> new OllamaProvider(
                        base, "m", 1, new ObjectMapper()
                ).complete("s", "u")
        );

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
    }

    @Test
    void emptyAnswerBecomes502() {
        respond(
                "/api/chat",
                200,
                "{\"message\":{\"content\":\"   \"}}",
                0
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> new OllamaProvider(
                        base, "m", 5, new ObjectMapper()
                ).complete("s", "u")
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void unreachableProviderBecomes502() throws IOException {
        int port;

        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        ApiException exception = assertThrows(
                ApiException.class,
                () -> new OllamaProvider(
                        "http://127.0.0.1:" + port,
                        "m",
                        2,
                        new ObjectMapper()
                ).complete("s", "u")
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }
}
