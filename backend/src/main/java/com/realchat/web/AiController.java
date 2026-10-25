package com.realchat.web;

import com.realchat.service.AiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AiController {
    public record TranslateReq(@NotBlank @Size(max = 10) String targetLang) {}
    public record SummaryReq(Integer lastMessages, Integer sinceHours) {}

    private final AiService ai;

    @GetMapping("/api/ai/status")
    public AiService.Status status() { return ai.status(); }

    @PostMapping("/api/messages/{id}/translate")
    public AiService.Translation translate(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody TranslateReq r) { return ai.translate(id, me, r.targetLang()); }

    @PostMapping("/api/conversations/{id}/summary")
    public AiService.Summary summary(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestBody(required = false) SummaryReq r) {
        return ai.summarize(id, me, r == null ? null : r.lastMessages(), r == null ? null : r.sinceHours());
    }
}