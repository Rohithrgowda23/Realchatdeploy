package com.realchat.web;

import com.realchat.dto.Dto.MessageDto;
import com.realchat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Saved messages are strictly per-user: every query is keyed by the authenticated user id. */
@RestController
@RequestMapping("/api/saved")
@RequiredArgsConstructor
public class SavedController {
    private final ChatService chat;

    @GetMapping
    public List<MessageDto> list(@AuthenticationPrincipal Long me, @RequestParam(defaultValue = "") String q,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return chat.savedMessages(me, q, page, size);
    }

    @GetMapping("/ids")
    public List<Long> ids(@AuthenticationPrincipal Long me) { return chat.savedIds(me); }

    @PostMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal Long me, @PathVariable Long messageId) { chat.save(messageId, me); }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal Long me, @PathVariable Long messageId) { chat.unsave(messageId, me); }
}
