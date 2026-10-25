package com.realchat.web;

import com.realchat.dto.Dto.*;
import com.realchat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final ChatService chat;

    @PutMapping("/{id}")
    public MessageDto edit(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody EditReq r) { return chat.edit(id, me, r.content()); }

    @DeleteMapping("/{id}")
    public MessageDto delete(@AuthenticationPrincipal Long me, @PathVariable Long id) { return chat.delete(id, me); }

    @PostMapping("/{id}/reactions")
    public MessageDto react(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody ReactReq r) { return chat.toggleReaction(id, me, r.emoji()); }

    @PostMapping("/{id}/forward")
    public MessageDto forward(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody ForwardReq r) {
        return chat.forward(id, me, r.conversationId(), r.caption());
    }

    /** "Delete for me": hides the message for the caller only. */
    @PostMapping("/{id}/hide")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void hide(@AuthenticationPrincipal Long me, @PathVariable Long id) { chat.hideForMe(id, me); }
}
