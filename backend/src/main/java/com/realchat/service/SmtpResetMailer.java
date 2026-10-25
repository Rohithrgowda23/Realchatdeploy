package com.realchat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Real email through any SMTP server (app.mail.mode=smtp). Configure spring.mail.* via SPRING_MAIL_* environment variables. */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpResetMailer implements ResetMailer {
    private final JavaMailSender sender;
    private final String from;
    private final long ttlMinutes;

    public SmtpResetMailer(JavaMailSender sender, @Value("${app.mail.from}") String from,
                           @Value("${app.password-reset.ttl-minutes:30}") long ttlMinutes) {
        this.sender = sender; this.from = from; this.ttlMinutes = ttlMinutes;
    }

    @Override
    public void sendResetLink(String toEmail, String link) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(toEmail);
        m.setSubject("Reset your RealChat password");
        m.setText("We received a request to reset your RealChat password.\n\nOpen this link within " + ttlMinutes
                + " minutes to choose a new password:\n" + link + "\n\nIf you did not ask for this, you can ignore this email.");
        sender.send(m);
    }
}