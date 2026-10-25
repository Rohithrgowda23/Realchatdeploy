package com.realchat.web;

import com.realchat.service.NotificationService;
import com.realchat.service.NotificationService.NotificationDto;
import com.realchat.service.NotificationService.Prefs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    public record ReadReq(List<Long> ids) {}
    private final NotificationService service;

    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal Long me, @RequestParam(required = false) Long before,
                                      @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return service.list(me, before, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal Long me) { return Map.of("count", service.unreadCount(me)); }

    /** No ids = mark everything read. */
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal Long me, @RequestBody(required = false) ReadReq r) { service.markRead(me, r == null ? null : r.ids()); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal Long me, @PathVariable Long id) { service.dismiss(me, id); }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Long me) { service.clear(me); }

    @GetMapping("/preferences")
    public Prefs prefs(@AuthenticationPrincipal Long me) { return service.getPrefs(me); }

    @PutMapping("/preferences")
    public Prefs setPrefs(@AuthenticationPrincipal Long me, @RequestBody Prefs p) { return service.setPrefs(me, p); }
}