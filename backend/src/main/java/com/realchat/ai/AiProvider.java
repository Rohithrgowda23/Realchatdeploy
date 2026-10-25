package com.realchat.ai;

/** Replaceable AI backend. Implementations never see anything except the text they are handed. */
public interface AiProvider {
    /** Runs one chat completion and returns plain text. Throws ApiException (503/502/504) on problems. */
    String complete(String system, String user);
    boolean available();
    String name();
    String model();
}