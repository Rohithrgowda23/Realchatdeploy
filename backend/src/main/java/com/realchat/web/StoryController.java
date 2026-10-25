package com.realchat.web;

import com.realchat.domain.Story;
import com.realchat.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {
    public record SettingsReq(String audience, List<Long> hiddenFrom) {}
    private final StoryService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StoryService.StoryDto create(@AuthenticationPrincipal Long me, @RequestParam String type, @RequestParam(required = false) String text,
                                        @RequestParam(required = false) String bgColor, @RequestParam(required = false) Integer ttlHours,
                                        @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.create(me, type, text, bgColor, ttlHours, file);
    }

    @GetMapping("/feed")
    public List<StoryService.FeedGroup> feed(@AuthenticationPrincipal Long me) { return service.feed(me); }

    @GetMapping("/{id}/media")
    public ResponseEntity<Resource> media(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        StoryService.MediaResult r = service.openMedia(id, me);
        Story s = r.story();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(s.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("story").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(r.resource());
    }

    @PostMapping("/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@AuthenticationPrincipal Long me, @PathVariable Long id) { service.markViewed(id, me); }

    @GetMapping("/{id}/viewers")
    public List<StoryService.Viewer> viewers(@AuthenticationPrincipal Long me, @PathVariable Long id) { return service.viewers(id, me); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long me, @PathVariable Long id) { service.delete(id, me); }

    @GetMapping("/settings")
    public StoryService.Settings settings(@AuthenticationPrincipal Long me) { return service.getSettings(me); }

    @PutMapping("/settings")
    public StoryService.Settings setSettings(@AuthenticationPrincipal Long me, @RequestBody SettingsReq r) { return service.setSettings(me, r.audience(), r.hiddenFrom()); }
}