package com.realchat.web;

import com.realchat.dto.Dto.SessionDto;
import com.realchat.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessions;

    private static String sid(Authentication a) { return a.getDetails() instanceof String s ? s : null; }
    private static Long uid(Authentication a) { return (Long) a.getPrincipal(); }

    @GetMapping
    public List<SessionDto> list(Authentication a) { return sessions.list(uid(a), sid(a)); }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication a) { if (sid(a) != null) sessions.revoke(uid(a), sid(a)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication a, @PathVariable String id) { sessions.revoke(uid(a), id); }

    @PostMapping("/revoke-others")
    public Map<String, Integer> revokeOthers(Authentication a) { return Map.of("revoked", sessions.revokeOthers(uid(a), sid(a))); }
}
