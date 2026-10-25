package com.realchat.web;

import com.realchat.domain.Attachment;
import com.realchat.dto.Dto.GalleryItem;
import com.realchat.dto.Dto.MessageDto;
import com.realchat.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class AttachmentController {
    private static final Set<String> INLINE_KINDS = Set.of("IMAGE", "VIDEO", "AUDIO", "VOICE");
    private final AttachmentService attachments;

    @PostMapping(value = "/api/conversations/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto upload(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestPart("file") MultipartFile file,
                             @RequestParam(required = false) String caption, @RequestParam(required = false) Long replyToId) {
        return attachments.upload(id, me, file, caption, replyToId);
    }

    @PostMapping(value = "/api/conversations/{id}/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto voice(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestPart("file") MultipartFile file,
                            @RequestParam int durationMs, @RequestParam(required = false) Long replyToId) {
        return attachments.uploadVoice(id, me, file, durationMs, replyToId);
    }

    /** Authorised download/stream. Content type is the server-detected one; nosniff + sandbox CSP keep it inert. Supports Range requests. */
    @GetMapping("/api/attachments/{id}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        AttachmentService.FileResult r = attachments.open(id, me);
        Attachment a = r.attachment();
        boolean inline = !download && (INLINE_KINDS.contains(a.getKind()) || "application/pdf".equals(a.getMimeType()));
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(a.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(r.resource());
    }

    @GetMapping("/api/conversations/{id}/media")
    public List<GalleryItem> media(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestParam(defaultValue = "ALL") String kind,
                                   @RequestParam(required = false) Long before, @RequestParam(defaultValue = "24") int size) {
        return attachments.gallery(id, me, kind, before, size);
    }
}