package com.realchat.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * STUN/TURN servers for WebRTC, from backend configuration only (nothing private is compiled into the frontend).
 * STUN lets browsers discover their public address; many home networks connect with STUN alone. Strict firewalls and
 * some mobile networks need a TURN relay. With CALL_TURN_SECRET (coturn "use-auth-secret") each user receives a TURN
 * login that expires after an hour, so no permanent TURN password is ever handed out.
 */
@Service
public class IceConfigService {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IceServer(List<String> urls, String username, String credential) {}

    private final List<String> stun;
    private final List<String> turn;
    private final String turnUsername;
    private final String turnCredential;
    private final String turnSecret;
    private final long ttlSeconds;

    public IceConfigService(@Value("${app.calls.stun-urls:stun:stun.l.google.com:19302}") String stun, @Value("${app.calls.turn-urls:}") String turn,
                            @Value("${app.calls.turn-username:}") String turnUsername, @Value("${app.calls.turn-credential:}") String turnCredential,
                            @Value("${app.calls.turn-secret:}") String turnSecret, @Value("${app.calls.turn-ttl-seconds:3600}") long ttlSeconds) {
        this.stun = split(stun); this.turn = split(turn); this.turnUsername = turnUsername; this.turnCredential = turnCredential; this.turnSecret = turnSecret; this.ttlSeconds = ttlSeconds;
    }

    private static List<String> split(String s) { return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::strip).filter(x -> !x.isEmpty()).toList(); }

    public List<IceServer> forUser(Long userId) { return forUser(userId, Instant.now()); }

    List<IceServer> forUser(Long userId, Instant now) {
        List<IceServer> out = new ArrayList<>();
        if (!stun.isEmpty()) out.add(new IceServer(stun, null, null));
        if (!turn.isEmpty()) {
            if (!turnSecret.isBlank()) {
                String username = (now.getEpochSecond() + ttlSeconds) + ":" + userId;
                out.add(new IceServer(turn, username, hmacBase64(turnSecret, username)));
            } else if (!turnUsername.isBlank()) {
                out.add(new IceServer(turn, turnUsername, turnCredential));
            }
        }
        return out;
    }

    private static String hmacBase64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}