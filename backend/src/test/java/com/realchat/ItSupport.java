package com.realchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Small helpers shared by the MySQL integration tests. */
final class ItSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    private ItSupport() {}

    record Account(long id, String email, String token) {}

    static Account register(MockMvc mvc, String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"correct-horse-1\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = JSON.readTree(body);
        return new Account(n.path("user").path("id").asLong(), email, n.path("token").asText());
    }

    static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, Account a) { return b.header("Authorization", "Bearer " + a.token()); }

    static JsonNode json(String s) throws Exception { return JSON.readTree(s); }

    static long directConversation(MockMvc mvc, Account a, Account b) throws Exception {
        String res = mvc.perform(auth(post("/api/conversations/direct"), a).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + b.id() + "}"))
                .andReturn().getResponse().getContentAsString();
        return json(res).path("id").asLong();
    }

    static long send(MockMvc mvc, Account a, long conv, String text) throws Exception {
        String res = mvc.perform(auth(post("/api/conversations/" + conv + "/messages"), a).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + text + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return json(res).path("id").asLong();
    }
}