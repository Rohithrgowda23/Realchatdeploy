package com.realchat;

import com.realchat.domain.OAuthAccount;
import com.realchat.domain.User;
import com.realchat.exception.ApiException;
import com.realchat.exception.OAuthException;
import com.realchat.repository.OAuthAccountRepository;
import com.realchat.repository.UserRepository;
import com.realchat.service.OAuthAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {
    @Mock OAuthAccountRepository accounts;
    @Mock UserRepository users;
    @InjectMocks OAuthAccountService service;

    private static OAuthAccount linked(Long user) {
        OAuthAccount a = new OAuthAccount(); a.setUserId(user); a.setProvider("google"); a.setProviderSubject("sub-1");
        return a;
    }

    @Test void returningUserMatchedBySubjectNotEmail() {
        when(accounts.findByProviderAndProviderSubject("google", "sub-1")).thenReturn(Optional.of(linked(5L)));
        assertEquals(5L, service.loginOrRegister("google", "sub-1", "changed@x.com", true, "A", null));
        verify(users, never()).save(any());
    }

    @Test void unverifiedEmailRejected() {
        when(accounts.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        assertEquals("unverified_email", assertThrows(OAuthException.class, () -> service.loginOrRegister("google", "s", "a@x.com", false, "A", null)).getReason());
        verify(users, never()).save(any());
    }

    @Test void existingPasswordAccountIsNotAutoLinkedByEmail() {
        when(accounts.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(users.existsByEmailIgnoreCase("a@x.com")).thenReturn(true);
        assertEquals("account_exists", assertThrows(OAuthException.class, () -> service.loginOrRegister("google", "s", "A@x.com", true, "A", null)).getReason());
        verify(accounts, never()).save(any());
    }

    @Test void newVerifiedUserIsCreatedWithoutPasswordAndLinked() {
        when(accounts.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(users.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(i -> { User u = i.getArgument(0); u.setId(9L); return u; });
        assertEquals(9L, service.loginOrRegister("google", "s", "New@x.com", true, "New Person", "https://img/x.png"));
        verify(users).save(argThat(u -> u.getPasswordHash() == null && "new@x.com".equals(u.getEmail())));
        verify(accounts).save(any(OAuthAccount.class));
    }

    @Test void cannotLinkGoogleAccountAlreadyOwnedByAnotherUser() {
        when(accounts.findByProviderAndProviderSubject("google", "sub-1")).thenReturn(Optional.of(linked(5L)));
        assertEquals("already_linked_other", assertThrows(OAuthException.class, () -> service.link(6L, "google", "sub-1", "a@x.com")).getReason());
    }

    @Test void cannotUnlinkOnlySignInMethod() {
        User u = new User(); u.setId(5L); u.setPasswordHash(null);
        when(accounts.findByUserIdAndProvider(5L, "google")).thenReturn(Optional.of(linked(5L)));
        when(users.findById(5L)).thenReturn(Optional.of(u));
        when(accounts.countByUserId(5L)).thenReturn(1L);
        assertThrows(ApiException.class, () -> service.unlink(5L, "google"));
        verify(accounts, never()).delete(any());
    }

    @Test void canUnlinkWhenPasswordExists() {
        User u = new User(); u.setId(5L); u.setPasswordHash("$2a$hash");
        OAuthAccount a = linked(5L);
        when(accounts.findByUserIdAndProvider(5L, "google")).thenReturn(Optional.of(a));
        when(users.findById(5L)).thenReturn(Optional.of(u));
        service.unlink(5L, "google");
        verify(accounts).delete(a);
    }
}