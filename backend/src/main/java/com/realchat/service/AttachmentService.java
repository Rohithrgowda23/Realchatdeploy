package com.realchat.service;

import com.realchat.domain.Attachment;
import com.realchat.domain.ChatMessage;
import com.realchat.domain.User;
import com.realchat.dto.Dto.GalleryItem;
import com.realchat.dto.Dto.MessageDto;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.FileTypeDetector.Detected;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Upload, download and gallery for attachments and voice messages. Every path checks current conversation membership
 * in the database; deleted or hidden messages never serve their file.
 */
@Service
public class AttachmentService {
    public record FileResult(Attachment attachment, Resource resource) {}

    private final FileStorage storage;
    private final AttachmentRepository attachments;
    private final MessageRepository messages;
    private final MemberRepository members;
    private final HiddenRepository hidden;
    private final UserRepository users;
    private final ChatService chat;
    private final long maxBytes;
    private final long maxVoiceBytes;

    public AttachmentService(FileStorage storage, AttachmentRepository attachments, MessageRepository messages, MemberRepository members,
                             HiddenRepository hidden, UserRepository users, ChatService chat,
                             @Value("${app.storage.max-bytes:26214400}") long maxBytes,
                             @Value("${app.storage.max-voice-bytes:10485760}") long maxVoiceBytes) {
        this.storage = storage; this.attachments = attachments; this.messages = messages; this.members = members;
        this.hidden = hidden; this.users = users; this.chat = chat; this.maxBytes = maxBytes; this.maxVoiceBytes = maxVoiceBytes;
    }

    private void requireMember(Long convId, Long me) {
        members.findByConversationIdAndUserId(convId, me).orElseThrow(() -> ApiException.notFound("Conversation not found"));
    }

    public MessageDto upload(Long convId, Long me, MultipartFile file, String caption, Long replyToId) {
        requireMember(convId, me);
        return store(convId, me, file, caption, replyToId, false, null);
    }

    public MessageDto uploadVoice(Long convId, Long me, MultipartFile file, int durationMs, Long replyToId) {
        requireMember(convId, me);
        if (durationMs < 500 || durationMs > 600_000) throw ApiException.bad("A voice message must be between 0.5 seconds and 10 minutes");
        return store(convId, me, file, null, replyToId, true, durationMs);
    }

    private MessageDto store(Long convId, Long me, MultipartFile file, String caption, Long replyToId, boolean voice, Integer durationMs) {
        if (file == null || file.isEmpty()) throw ApiException.bad("No file received");
        long limit = voice ? maxVoiceBytes : maxBytes;
        if (file.getSize() > limit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (limit " + (limit / 1_048_576) + " MB)");
        byte[] head;
        try (InputStream in = file.getInputStream()) { head = in.readNBytes(32); } catch (IOException e) { throw ApiException.bad("The file could not be read"); }
        String display = FileTypeDetector.safeName(file.getOriginalFilename());
        Detected d = FileTypeDetector.detect(head, display, voice)
                .orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file type is not allowed"));
        if (voice && !"AUDIO".equals(d.kind())) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That is not an audio recording");

        String stored = storage.store(file);
        try {
            return chat.sendWithAttachment(convId, me, caption, replyToId, msg -> {
                Attachment a = new Attachment();
                a.setMessageId(msg.getId()); a.setConversationId(convId);
                a.setKind(voice ? "VOICE" : d.kind());
                a.setOriginalName(display); a.setStoredName(stored); a.setMimeType(d.mime()); a.setSizeBytes(file.getSize()); a.setDurationMs(durationMs);
                attachments.save(a);
            });
        } catch (RuntimeException e) {
            storage.deleteQuietly(stored); // rejected (not a member, announcement mode, rate limit...): leave nothing behind
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public FileResult open(Long attachmentId, Long me) {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> ApiException.notFound("File not found"));
        requireMember(a.getConversationId(), me);
        ChatMessage m = messages.findById(a.getMessageId()).orElseThrow(() -> ApiException.notFound("File not found"));
        if (m.isDeleted() || hidden.existsByUserIdAndMessageId(me, m.getId())) throw ApiException.notFound("File not found");
        return new FileResult(a, storage.load(a.getStoredName()));
    }

    @Transactional(readOnly = true)
    public List<GalleryItem> gallery(Long convId, Long me, String kind, Long before, int size) {
        requireMember(convId, me);
        List<String> kinds = switch (kind == null ? "ALL" : kind.toUpperCase(Locale.ROOT)) {
            case "ALL" -> List.of("IMAGE", "VIDEO", "AUDIO", "VOICE", "DOCUMENT");
            case "IMAGE" -> List.of("IMAGE");
            case "VIDEO" -> List.of("VIDEO");
            case "AUDIO" -> List.of("AUDIO", "VOICE");
            case "DOCUMENT" -> List.of("DOCUMENT");
            default -> throw ApiException.bad("Unknown media type");
        };
        List<Attachment> list = attachments.gallery(convId, kinds, before == null ? Long.MAX_VALUE : before, me, PageRequest.of(0, Math.min(Math.max(size, 1), 60)));
        if (list.isEmpty()) return List.of();
        Map<Long, ChatMessage> msgs = messages.findAllById(list.stream().map(Attachment::getMessageId).toList()).stream().collect(Collectors.toMap(ChatMessage::getId, x -> x));
        Map<Long, String> names = users.findAllById(msgs.values().stream().map(ChatMessage::getSenderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        return list.stream().map(a -> {
            ChatMessage m = msgs.get(a.getMessageId());
            return new GalleryItem(a.getId(), a.getKind(), a.getOriginalName(), a.getMimeType(), a.getSizeBytes(), a.getDurationMs(),
                    a.getMessageId(), m == null ? "Unknown" : names.getOrDefault(m.getSenderId(), "Unknown"), a.getCreatedAt());
        }).toList();
    }
}