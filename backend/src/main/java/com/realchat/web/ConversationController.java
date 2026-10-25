package com.realchat.web;

import com.realchat.dto.Dto.*;
import com.realchat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ChatService chat;

    @GetMapping
    public List<ConversationDto> list(@AuthenticationPrincipal Long me) { return chat.list(me); }

    @PostMapping("/direct")
    public ConversationDto direct(@AuthenticationPrincipal Long me, @Valid @RequestBody DirectReq req) { return chat.createDirect(me, req.userId()); }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto group(@AuthenticationPrincipal Long me, @Valid @RequestBody GroupReq r) {
        return chat.createGroup(me, r.name(), r.description(), r.avatarUrl(), r.memberIds());
    }

    @GetMapping("/{id}")
    public ConversationDetail detail(@AuthenticationPrincipal Long me, @PathVariable Long id) { return chat.detail(id, me); }

    @PatchMapping("/{id}")
    public ConversationDetail update(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody GroupUpdateReq r) {
        return chat.updateGroup(id, me, r.name(), r.description(), r.avatarUrl());
    }

    @PatchMapping("/{id}/settings")
    public ConversationDto settings(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestBody SettingsReq r) {
        return chat.updateSettings(id, me, r.pinned(), r.muted(), r.archived());
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@AuthenticationPrincipal Long me, @PathVariable Long id,
                                     @RequestParam(required = false) Long before, @RequestParam(defaultValue = "30") int size) {
        return chat.history(id, me, before, size);
    }

    @GetMapping("/{id}/messages/search")
    public List<MessageDto> search(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestParam String q) {
        return chat.searchMessages(id, me, q);
    }

    /** REST fallback used by the client when the WebSocket is down. Same rules as the WebSocket path. */
    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody SendReq r) {
        return chat.send(id, me, r.content(), r.replyToId(), r.mentionIds());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestBody(required = false) ReadReq r) {
        chat.markRead(id, me, r == null ? null : r.messageId());
    }

    @PostMapping("/{id}/members")
    public ConversationDetail addMembers(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody AddMembersReq r) {
        return chat.addMembers(id, me, r.userIds());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ConversationDetail remove(@AuthenticationPrincipal Long me, @PathVariable Long id, @PathVariable Long userId) {
        return chat.removeMember(id, me, userId);
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ConversationDetail role(@AuthenticationPrincipal Long me, @PathVariable Long id, @PathVariable Long userId, @Valid @RequestBody RoleReq r) {
        return chat.setRole(id, me, userId, r.role());
    }

    @PostMapping("/{id}/transfer")
    public ConversationDetail transfer(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody TransferReq r) {
        return chat.transferOwnership(id, me, r.userId());
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Long me, @PathVariable Long id) { chat.leave(id, me); }

    @PatchMapping("/{id}/announcement")
    public ConversationDto announcement(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestBody AnnouncementReq r) {
        return chat.setAnnouncement(id, me, r.enabled());
    }

    @GetMapping("/{id}/audit")
    public List<AuditDto> audit(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestParam(required = false) String action,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return chat.auditLog(id, me, action, page, size);
    }

    @GetMapping("/{id}/mentions")
    public List<MessageDto> mentions(@AuthenticationPrincipal Long me, @PathVariable Long id,
                                     @RequestParam(required = false) Long before, @RequestParam(defaultValue = "30") int size) {
        return chat.mentionsOfMe(id, me, before, size);
    }
}