package com.realchat.web;

import com.realchat.dto.Dto.MessageDto;
import com.realchat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
    private final ChatService chat;

    /** Searches only conversations the caller currently belongs to. from/to are ISO-8601 instants. */
    @GetMapping("/messages")
    public List<MessageDto> messages(@AuthenticationPrincipal Long me, @RequestParam(defaultValue = "") String q,
                                     @RequestParam(required = false) Long conversationId, @RequestParam(required = false) Long senderId,
                                     @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return chat.searchAll(me, q, conversationId, senderId, from, to, page, size);
    }
}
