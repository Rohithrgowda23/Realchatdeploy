package com.realchat.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** DEVELOPMENT ONLY (app.mail.mode=console, the default): prints the link to the backend log instead of sending email. */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleResetMailer implements ResetMailer {
    private static final Logger log = LoggerFactory.getLogger(ConsoleResetMailer.class);

    @PostConstruct
    void warn() { log.warn("Mail mode is 'console': password-reset links are printed to this log. Set app.mail.mode=smtp in production."); }

    @Override
    public void sendResetLink(String toEmail, String link) { log.info("[DEV MAIL] Password reset for {}: {}", toEmail, link); }
}