package com.realchat.security;

import com.realchat.service.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Cancelled consent, bad state/nonce, invalid or expired token, provider outage: all end here with a generic reason. */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final String failureRedirect;
    private final String frontendBase;

    public OAuth2LoginFailureHandler(@Value("${app.oauth.failure-redirect}") String failureRedirect,
                                     @Value("${app.oauth.frontend-base-url}") String frontendBase) {
        this.failureRedirect = failureRedirect; this.frontendBase = frontendBase;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        boolean linking = false;
        HttpSession s = req.getSession(false);
        if (s != null) { linking = s.getAttribute(OAuthAccountService.LINK_SESSION_ATTR) != null; s.invalidate(); }
        boolean denied = ex instanceof OAuth2AuthenticationException o && "access_denied".equals(o.getError().getErrorCode());
        String reason = denied ? "denied" : "failed";
        log.warn("OAuth login rejected: {}", ex.getClass().getSimpleName());
        res.sendRedirect(linking ? OAuthRedirects.with(frontendBase + "/", "oauth_link", reason) : OAuthRedirects.with(failureRedirect, "reason", reason));
    }
}