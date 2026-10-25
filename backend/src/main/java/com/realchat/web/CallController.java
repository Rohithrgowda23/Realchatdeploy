package com.realchat.web;

import com.realchat.service.CallService;
import com.realchat.service.IceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {
    private final CallService calls;
    private final IceConfigService ice;

    /** STUN/TURN for the signed-in user. TURN logins are short-lived when a shared secret is configured. */
    @GetMapping("/ice-config")
    public Map<String, List<IceConfigService.IceServer>> iceConfig(@AuthenticationPrincipal Long me) { return Map.of("iceServers", ice.forUser(me)); }

    @GetMapping
    public List<CallService.CallView> history(@AuthenticationPrincipal Long me, @RequestParam(required = false) Long before, @RequestParam(defaultValue = "30") int size) {
        return calls.history(me, before, size);
    }

    @GetMapping("/missed-count")
    public Map<String, Long> missed(@AuthenticationPrincipal Long me) { return Map.of("count", calls.missedCount(me)); }

    @PostMapping("/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seen(@AuthenticationPrincipal Long me) { calls.markSeen(me); }
}