package com.realchat.dto;

import com.realchat.domain.User;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response shapes. JPA entities are never serialised directly. */
public final class Dto {
    private Dto() {}

    // ---- auth / users
    public record RegisterReq(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name is too long") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 190) String email,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password) {}
    public record LoginReq(@NotBlank(message = "Email is required") String email, @NotBlank(message = "Password is required") String password) {}
    public record UserDto(Long id, String name, String email, String about, String avatarUrl) {
        public static UserDto of(User u, boolean withEmail) {
            return new UserDto(u.getId(), u.getName(), withEmail ? u.getEmail() : null, u.getAbout(), u.getAvatarUrl());
        }
    }
    public record AuthRes(String token, UserDto user) {}
    public record ProfileReq(
            @Size(min = 1, max = 100, message = "Name must be 1-100 characters") String name,
            @Size(max = 255) String about,
            @Pattern(regexp = "^(https?://.*)?$", message = "Avatar must be an http(s) URL") @Size(max = 500) String avatarUrl) {}

    // ---- conversations
    public record DirectReq(@NotNull Long userId) {}
    public record GroupReq(
            @NotBlank(message = "Group name is required") @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Pattern(regexp = "^(https?://.*)?$", message = "Avatar must be an http(s) URL") @Size(max = 500) String avatarUrl,
            @Size(max = 256) List<Long> memberIds) {}
    public record GroupUpdateReq(
            @Size(min = 1, max = 100) String name,
            @Size(max = 500) String description,
            @Pattern(regexp = "^(https?://.*)?$", message = "Avatar must be an http(s) URL") @Size(max = 500) String avatarUrl) {}
    public record AddMembersReq(@NotEmpty @Size(max = 256) List<Long> userIds) {}
    public record RoleReq(@NotBlank String role) {}
    public record TransferReq(@NotNull Long userId) {}
    public record SettingsReq(Boolean pinned, Boolean muted, Boolean archived) {}
    public record ReadReq(Long messageId) {}

    public record LastMessage(Long id, Long senderId, String preview, Instant createdAt) {}
    public record ConversationDto(Long id, String type, String title, String description, String avatarUrl, Long otherUserId,
                                  LastMessage lastMessage, long unread, boolean pinned, boolean muted, boolean archived,
                                  int memberCount, String myRole, Instant createdAt, boolean announcementOnly, long unreadMentions) {}
    public record MemberDto(Long userId, String name, String avatarUrl, String role, long lastReadMessageId, long lastDeliveredMessageId) {}
    public record ConversationDetail(ConversationDto conversation, List<MemberDto> members) {}

    // ---- messages
    public record SendReq(@NotBlank @Size(max = 4000) String content, Long replyToId, @Size(max = 20) List<Long> mentionIds) {}
    public record EditReq(@NotBlank @Size(max = 4000) String content) {}
    public record ReactReq(@NotBlank @Size(max = 16) String emoji) {}
    public record ReplyRef(Long id, String senderName, String content) {}
    public record MessageDto(Long id, Long conversationId, Long senderId, String senderName, String content, ReplyRef replyTo,
                             boolean edited, boolean deleted, Instant createdAt, Map<String, List<Long>> reactions, String forwardedFrom, AttachmentDto attachment, List<MentionDto> mentions) {}
    public record MentionDto(Long userId, String name) {}
    public record AttachmentDto(Long id, String kind, String fileName, String mimeType, long sizeBytes, Integer durationMs) {}
    public record GalleryItem(Long id, String kind, String fileName, String mimeType, long sizeBytes, Integer durationMs, Long messageId, String senderName, Instant createdAt) {}

    public record AnnouncementReq(boolean enabled) {}
    public record ForwardReq(@NotNull Long conversationId, @Size(max = 1000) String caption) {}
    public record AuditDto(Long id, Long actorId, String actorName, String action, Long targetId, String targetName, String detail, Instant createdAt) {}
    public record SessionDto(String id, String device, String ip, Instant createdAt, Instant lastSeenAt, boolean current) {}

    // ---- websocket envelope
    public record Event(String type, Object data) {}
}