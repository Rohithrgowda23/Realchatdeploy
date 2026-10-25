package com.realchat.web;

import com.realchat.domain.OAuthAccount;
import com.realchat.domain.User;
import com.realchat.dto.Dto.AuthRes;
import com.realchat.dto.Dto.UserDto;
import com.realchat.exception.ApiException;
import com.realchat.repository.UserRepository;
import com.realchat.security.JwtService;
import com.realchat.service.OAuthAccountService;
import com.realchat.service.OneTimeCodeStore;
import com.realchat.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class OAuthController {
    public record ExchangeReq(@NotBlank String code) {}
    public record LinkedProvider(String provider, String email, Instant linkedAt) {}
    public record AccountsRes(boolean hasPassword, List<LinkedProvider> providers) {}

    private final OneTimeCodeStore codes;
    private final OAuthAccountService accounts;
    private final UserRepository users;
    private final JwtService jwt;
    private final SessionService sessions;
    private final String googleClientId;
    private final String backendBase;
    private final String frontendBase;

    public OAuthController(OneTimeCodeStore codes, OAuthAccountService accounts, UserRepository users, JwtService jwt, SessionService sessions,
                           @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
                           @Value("${app.oauth.backend-base-url}") String backendBase,
                           @Value("${app.oauth.frontend-base-url}") String frontendBase) {
        this.codes = codes; this.accounts = accounts; this.users = users; this.jwt = jwt; this.sessions = sessions;
        this.googleClientId = googleClientId; this.backendBase = backendBase; this.frontendBase = frontendBase;
    }

    private boolean googleEnabled() { return googleClientId != null && !googleClientId.isBlank() && !"disabled".equals(googleClientId); }

    /** Lets the UI hide the Google button when no credentials are configured. */
    @GetMapping("/api/auth/oauth/providers")
    public Map<String, Boolean> providers() { return Map.of("google", googleEnabled()); }

    /** Step 2 of sign-in: the SPA swaps the single-use code for a normal RealChat session + JWT. */
    @PostMapping("/api/auth/oauth/exchange")
    public AuthRes exchange(@Valid @RequestBody ExchangeReq req, HttpServletRequest http) {
        Long userId = codes.consumeLogin(req.code())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "This sign-in link expired or was already used"));
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
        String sid = sessions.create(u.getId(), http.getHeader("User-Agent"), http.getRemoteAddr(), jwt.getExpirationMinutes());
        return new AuthRes(jwt.generate(u.getId(), u.getName(), sid), UserDto.of(u, true));
    }

    @GetMapping("/api/oauth/accounts")
    public AccountsRes list(@AuthenticationPrincipal Long me) {
        List<LinkedProvider> l = accounts.list(me).stream().map((OAuthAccount a) -> new LinkedProvider(a.getProvider(), a.getEmail(), a.getCreatedAt())).toList();
        return new AccountsRes(accounts.hasPassword(me), l);
    }

    /** A signed-in user asks to connect Google. Returns a single-use URL for the browser to navigate to. */
    @PostMapping("/api/oauth/link-intent")
    public Map<String, String> linkIntent(@AuthenticationPrincipal Long me) {
        if (!googleEnabled()) throw ApiException.bad("Google sign-in is not configured");
        return Map.of("beginUrl", backendBase + "/oauth2/link/begin?intent=" + codes.issueLink(me));
    }

    @DeleteMapping("/api/oauth/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@AuthenticationPrincipal Long me, @PathVariable String provider) { accounts.unlink(me, provider); }

    /** Browser navigation (no Authorization header possible): bind the verified user to this OAuth attempt, then start Google. */
    @GetMapping("/oauth2/link/begin")
    public void beginLink(@RequestParam String intent, HttpServletRequest req, HttpServletResponse res) throws IOException {
        Optional<Long> uid = codes.consumeLink(intent);
        if (uid.isEmpty()) { res.sendRedirect(frontendBase + "/?oauth_link=expired"); return; }
        req.getSession(true).setAttribute(OAuthAccountService.LINK_SESSION_ATTR, uid.get());
        res.sendRedirect("/oauth2/authorization/google");
    }
}