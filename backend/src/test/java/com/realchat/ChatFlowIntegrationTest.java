package com.realchat;

import com.realchat.ItSupport.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static com.realchat.ItSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@MySqlIntegrationTest
class ChatFlowIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void directConversationIsUniqueAndMessagesArePersisted() throws Exception {
        Account a = register(mvc, "Ann"), b = register(mvc, "Bob");
        long c1 = directConversation(mvc, a, b), c2 = directConversation(mvc, b, a);
        assertEquals(c1, c2); // no duplicate direct conversation between the same two people
        long m = send(mvc, a, c1, "hello bob");
        mvc.perform(auth(get("/api/conversations/" + c1 + "/messages"), b)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(m)).andExpect(jsonPath("$[0].content").value("hello bob"));
    }

    @Test void outsidersCannotReadSendSearchOrSaveAnything() throws Exception {
        Account a = register(mvc, "Ann"), b = register(mvc, "Bob"), eve = register(mvc, "Eve");
        long conv = directConversation(mvc, a, b);
        long m = send(mvc, a, conv, "top-secret-plan-" + System.nanoTime());
        mvc.perform(auth(get("/api/conversations/" + conv), eve)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/conversations/" + conv + "/messages"), eve)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/conversations/" + conv + "/messages"), eve).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/search/messages").param("q", "top-secret-plan"), eve)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(post("/api/saved/" + m), eve)).andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/messages/" + m), b).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"edited by bob\"}")).andExpect(status().isForbidden());
    }

    @Test void deleteForMeHidesOnlyForTheCaller() throws Exception {
        Account a = register(mvc, "Ann"), b = register(mvc, "Bob");
        long conv = directConversation(mvc, a, b);
        long m = send(mvc, a, conv, "ephemeral");
        mvc.perform(auth(post("/api/messages/" + m + "/hide"), b)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/conversations/" + conv + "/messages"), b)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(get("/api/conversations/" + conv + "/messages"), a)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void groupPermissionsAndAnnouncementModeAreEnforcedByTheServer() throws Exception {
        Account owner = register(mvc, "Own"), member = register(mvc, "Mem"), other = register(mvc, "Oth");
        String res = mvc.perform(auth(post("/api/conversations/groups"), owner).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Team\",\"memberIds\":[" + member.id() + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long g = json(res).path("id").asLong();
        mvc.perform(auth(post("/api/conversations/" + g + "/members"), member).contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[" + other.id() + "]}")).andExpect(status().isForbidden());
        mvc.perform(auth(patch("/api/conversations/" + g + "/announcement"), member).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isForbidden());
        mvc.perform(auth(patch("/api/conversations/" + g + "/announcement"), owner).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk());
        mvc.perform(auth(post("/api/conversations/" + g + "/messages"), member).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}")).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/conversations/" + g + "/messages"), owner).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"announcement\"}")).andExpect(status().isCreated());
    }

    @Test void mentioningSomeoneOutsideTheConversationIsRejected() throws Exception {
        Account a = register(mvc, "Ann"), b = register(mvc, "Bob"), stranger = register(mvc, "Str");
        long conv = directConversation(mvc, a, b);
        mvc.perform(auth(post("/api/conversations/" + conv + "/messages"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hey @Str\",\"mentionIds\":[" + stranger.id() + "]}")).andExpect(status().isBadRequest());
    }

    @Test void attachmentsAreValidatedByContentAndAuthorizedPerConversation() throws Exception {
        Account a = register(mvc, "Ann"), b = register(mvc, "Bob"), eve = register(mvc, "Eve");
        long conv = directConversation(mvc, a, b);
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        String res = mvc.perform(multipart("/api/conversations/" + conv + "/attachments").file(new MockMultipartFile("file", "pic.png", "image/png", png)).header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long attachmentId = json(res).path("attachment").path("id").asLong();
        mvc.perform(multipart("/api/conversations/" + conv + "/attachments").file(new MockMultipartFile("file", "evil.png", "image/png", "<html><script>x</script></html>".getBytes())).header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isUnsupportedMediaType()); // the declared type is ignored, the bytes decide
        mvc.perform(auth(get("/api/attachments/" + attachmentId), b)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(auth(get("/api/attachments/" + attachmentId), eve)).andExpect(status().isNotFound());
    }
}