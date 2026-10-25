package com.realchat.security;

import org.springframework.web.util.UriComponentsBuilder;

final class OAuthRedirects {
    private OAuthRedirects() {}
    static String with(String base, String key, String value) {
        return UriComponentsBuilder.fromUriString(base).queryParam(key, value).build().toUriString();
    }
}