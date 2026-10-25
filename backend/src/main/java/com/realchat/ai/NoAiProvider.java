package com.realchat.ai;

import com.realchat.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Default: no provider configured, AI features report "unavailable" and nothing is ever sent anywhere. */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "disabled", matchIfMissing = true)
public class NoAiProvider implements AiProvider {
    public String complete(String system, String user) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI features are not configured on this server"); }
    public boolean available() { return false; }
    public String name() { return "none"; }
    public String model() { return ""; }
}