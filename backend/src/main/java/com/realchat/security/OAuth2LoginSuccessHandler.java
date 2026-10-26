package com.realchat.security;

import com.realchat.exception.OAuthException;
import com.realchat.service.OAuthAccountService;
import com.realchat.service.OneTimeCodeStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final OAuthAccountService accounts;
    private final OneTimeCodeStore codes;
    private final String successRedirect;
    private final String failureRedirect;
    private final String frontendBase;

    public OAuth2LoginSuccessHandler(OAuthAccountService accounts, OneTimeCodeStore codes,
                                     @Value("${app.oauth.success-redirect}") String successRedirect,
                                     @Value("${app.oauth.failure-redirect}") String failureRedirect,
                                     @Value("${app.oauth.frontend-base-url}") String frontendBase) {
        this.accounts = accounts; this.codes = codes;
        this.successRedirect = successRedirect; this.failureRedirect = failureRedirect; this.frontendBase = frontendBase;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws IOException {
        Long linkUser = null;
        HttpSession session = req.getSession(false);
        if (session != null) {
            if (session.getAttribute(OAuthAccountService.LINK_SESSION_ATTR) instanceof Long l) linkUser = l;
            session.invalidate(); // the app is otherwise stateless; the session only carried state/nonce/PKCE
        }
        boolean linking = linkUser != null;
        try {
            if (!(auth instanceof OAuth2AuthenticationToken token) || !"google".equals(token.getAuthorizedClientRegistrationId())
                    || !(token.getPrincipal() instanceof OidcUser oidc)) throw new OAuthException("invalid_provider");

            String sub = oidc.getSubject();
            if (sub == null || sub.isBlank()) throw new OAuthException("failed");

            if (linking) {
                accounts.link(linkUser, "google", sub, oidc.getEmail());
                res.sendRedirect(OAuthRedirects.with(frontendBase + "/", "oauth_link", "ok"));
                return;
            }
            Long userId;
            try {
                userId = accounts.loginOrRegister("google", sub, oidc.getEmail(), Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName(), oidc.getPicture());
            } catch (DataIntegrityViolationException race) { // two first-time logins at once: the loser simply looks the account up
                userId = accounts.loginOrRegister("google", sub, oidc.getEmail(), Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName(), oidc.getPicture());
            }
            res.sendRedirect(OAuthRedirects.with(successRedirect, "code", codes.issueLogin(userId)));
        } catch (OAuthException e) {
            fail(res, linking, e.getReason());
        } catch (Exception e) {
            log.warn("OAuth sign-in failed: {}", e.getClass().getSimpleName()); // no tokens, codes or identity payloads in logs
            fail(res, linking, "failed");
        }
    }

    private void fail(HttpServletResponse res, boolean linking, String reason) throws IOException {
        res.sendRedirect(linking ? OAuthRedirects.with(frontendBase + "/", "oauth_link", reason)
                : OAuthRedirects.with(failureRedirect, "reason", reason));
    }
}