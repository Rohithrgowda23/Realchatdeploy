package com.realchat.service;

/** Replaceable delivery channel for password-reset links. */
public interface ResetMailer {
    void sendResetLink(String toEmail, String link);
}