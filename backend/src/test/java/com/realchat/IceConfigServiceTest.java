package com.realchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realchat.service.IceConfigService;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class IceConfigServiceTest {
    @Test void stunOnlyByDefaultAndNoCredentialsAreInvented() throws Exception {
        IceConfigService s = new IceConfigService("stun:stun.example.org:3478", "", "", "", "", 3600);
        var list = s.forUser(7L);
        assertEquals(1, list.size());
        String json = new ObjectMapper().writeValueAsString(list);
        assertFalse(json.contains("username") || json.contains("credential") || json.contains("null"));
    }
    @Test void turnWithSharedSecretGivesAShortLivedPerUserLogin() throws Exception {
        IceConfigService s = new IceConfigService("stun:s", "turn:t.example.org:3478", "", "", "topsecret", 3600);
        long before = Instant.now().getEpochSecond();
        var turn = s.forUser(7L).get(1);
        assertTrue(turn.username().endsWith(":7"));
        long expiry = Long.parseLong(turn.username().split(":")[0]);
        assertTrue(expiry >= before + 3600 && expiry <= before + 3700, "login should expire in about one hour");
        Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec("topsecret".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        assertEquals(Base64.getEncoder().encodeToString(mac.doFinal(turn.username().getBytes(StandardCharsets.UTF_8))), turn.credential());
        assertNotEquals("topsecret", turn.credential());
        assertNotEquals(s.forUser(8L).get(1).username(), turn.username());
    }
    @Test void staticTurnLoginIsUsedWhenNoSecretIsSet() {
        IceConfigService s = new IceConfigService("stun:s", "turn:t,turns:t:5349", "user", "pass", "", 3600);
        var turn = s.forUser(7L).get(1);
        assertEquals(2, turn.urls().size());
        assertEquals("user", turn.username());
    }
    @Test void turnUrlsWithoutAnyLoginAreNotOffered() {
        IceConfigService s = new IceConfigService("stun:s", "turn:t", "", "", "", 3600);
        assertEquals(1, s.forUser(7L).size());
    }
}