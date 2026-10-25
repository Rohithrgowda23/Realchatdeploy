package com.realchat;

import com.realchat.ItSupport.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static com.realchat.ItSupport.auth;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@MySqlIntegrationTest
class AuthFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void registrationStoresABcryptHashNeverThePassword() throws Exception {
        Account a = ItSupport.register(mvc, "Hash");
        String stored = jdbc.queryForObject("select password_hash from users where email = ?", String.class, a.email());
        assertNotEquals("correct-horse-1", stored);
        assertTrue(stored.startsWith("$2"));
    }

    @Test void loginRejectsWrongPasswordAndAcceptsTheRightOne() throws Exception {
        Account a = ItSupport.register(mvc, "Login");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + a.email() + "\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + a.email() + "\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test void protectedEndpointsNeedAValidToken() throws Exception {
        Account a = ItSupport.register(mvc, "Guard");
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/users/me"), a)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(a.email()));
    }

    @Test void loggingOutReallyInvalidatesTheToken() throws Exception {
        Account a = ItSupport.register(mvc, "Logout");
        mvc.perform(auth(get("/api/users/me"), a)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/sessions/current"), a)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/users/me"), a)).andExpect(status().isUnauthorized()); // the JWT is still validly signed, the session is revoked
    }

    @Test void duplicateEmailAndBadInputAreRejectedClearly() throws Exception {
        Account a = ItSupport.register(mvc, "Dup");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"email\":\"" + a.email() + "\",\"password\":\"correct-horse-1\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists()).andExpect(jsonPath("$.fields.password").exists());
    }
}