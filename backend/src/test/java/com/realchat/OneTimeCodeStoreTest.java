package com.realchat;

import com.realchat.service.OneTimeCodeStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class OneTimeCodeStoreTest {
    @Test void codeIsSingleUse() {
        OneTimeCodeStore s = new OneTimeCodeStore();
        String c = s.issueLogin(7L);
        assertEquals(7L, s.consumeLogin(c).orElseThrow());
        assertTrue(s.consumeLogin(c).isEmpty());
    }
    @Test void expiredCodeRejected() {
        OneTimeCodeStore s = new OneTimeCodeStore();
        assertTrue(s.consumeLogin(s.issueLogin(7L, Duration.ofSeconds(-1))).isEmpty());
    }
    @Test void unknownOrNullRejected() {
        OneTimeCodeStore s = new OneTimeCodeStore();
        assertTrue(s.consumeLogin("nope").isEmpty());
        assertTrue(s.consumeLogin(null).isEmpty());
    }
    @Test void loginAndLinkCodesAreNotInterchangeable() {
        OneTimeCodeStore s = new OneTimeCodeStore();
        assertTrue(s.consumeLink(s.issueLogin(7L)).isEmpty());
        assertTrue(s.consumeLogin(s.issueLink(7L)).isEmpty());
    }
}