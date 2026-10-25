package com.realchat.service;

import com.realchat.domain.OAuthAccount;
import com.realchat.domain.User;
import com.realchat.exception.ApiException;
import com.realchat.exception.OAuthException;
import com.realchat.repository.OAuthAccountRepository;
import com.realchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account policy:
 *  1. A returning user is matched by (provider, provider subject), never by email.
 *  2. A new Google user is created only if Google says the email is verified AND no RealChat account already uses it.
 *  3. If the email belongs to an existing account we do NOT link automatically (that would allow account takeover);
 *     the user must sign in to that account and connect Google from Settings.
 */
@Service
@RequiredArgsConstructor
public class OAuthAccountService {
    public static final String LINK_SESSION_ATTR = "rc.link.user";

    private final OAuthAccountRepository accounts;
    private final UserRepository users;

    @Transactional
    public Long loginOrRegister(String provider, String subject, String email, boolean emailVerified, String name, String picture) {
        var linked = accounts.findByProviderAndProviderSubject(provider, subject);
        if (linked.isPresent()) return linked.get().getUserId();

        if (email == null || email.isBlank() || !emailVerified) throw new OAuthException("unverified_email");
        String e = email.strip().toLowerCase();
        if (users.existsByEmailIgnoreCase(e)) throw new OAuthException("account_exists");

        User u = new User();
        String display = (name == null || name.isBlank()) ? e.substring(0, e.indexOf('@') < 1 ? e.length() : e.indexOf('@')) : name.strip();
        u.setName(display.length() > 100 ? display.substring(0, 100) : display);
        u.setEmail(e);
        u.setPasswordHash(null); // no password until the user sets one
        if (picture != null && picture.startsWith("https://") && picture.length() <= 500) u.setAvatarUrl(picture);
        u = users.save(u);
        saveLink(u.getId(), provider, subject, e);
        return u.getId();
    }

    /** Connect a provider to an already authenticated user. */
    @Transactional
    public void link(Long userId, String provider, String subject, String email) {
        var existing = accounts.findByProviderAndProviderSubject(provider, subject);
        if (existing.isPresent()) {
            if (existing.get().getUserId().equals(userId)) return;
            throw new OAuthException("already_linked_other");
        }
        if (accounts.existsByUserIdAndProvider(userId, provider)) throw new OAuthException("provider_already_connected");
        saveLink(userId, provider, subject, email);
    }

    @Transactional
    public void unlink(Long userId, String provider) {
        OAuthAccount a = accounts.findByUserIdAndProvider(userId, provider).orElseThrow(() -> ApiException.notFound("Not connected"));
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getPasswordHash() == null && accounts.countByUserId(userId) <= 1)
            throw ApiException.bad("You need another way to sign in before disconnecting this provider");
        accounts.delete(a);
    }

    @Transactional(readOnly = true)
    public boolean hasPassword(Long userId) { return users.findById(userId).map(u -> u.getPasswordHash() != null).orElse(false); }

    @Transactional(readOnly = true)
    public List<OAuthAccount> list(Long userId) { return accounts.findByUserId(userId); }

    private void saveLink(Long userId, String provider, String subject, String email) {
        OAuthAccount a = new OAuthAccount();
        a.setUserId(userId); a.setProvider(provider); a.setProviderSubject(subject); a.setEmail(email);
        accounts.save(a);
    }
}