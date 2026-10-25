package com.realchat.exception;

/** Carries a short machine-readable reason that is safe to show to the browser (never provider details). */
public class OAuthException extends RuntimeException {
    private final String reason;
    public OAuthException(String reason) { super(reason); this.reason = reason; }
    public String getReason() { return reason; }
}