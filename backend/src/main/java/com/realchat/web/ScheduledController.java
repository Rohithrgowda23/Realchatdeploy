package com.realchat.web;

import com.realchat.service.ScheduledMessageService;
import com.realchat.service.ScheduledMessageService.ScheduledDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ScheduledController {
    public record CreateReq(@NotBlank @Size(max = 4000) String content, Long replyToId, @NotNull Instant scheduledAt) {}
    public record UpdateReq(@Size(max = 4000) String content, Instant scheduledAt) {}

    private final ScheduledMessageService service;

    @PostMapping("/api/conversations/{id}/scheduled")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledDto create(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody CreateReq r) {
        return service.create(id, me, r.content(), r.replyToId(), r.scheduledAt());
    }

    @GetMapping("/api/scheduled")
    public List<ScheduledDto> list(@AuthenticationPrincipal Long me) { return service.list(me); }

    @PutMapping("/api/scheduled/{id}")
    public ScheduledDto update(@AuthenticationPrincipal Long me, @PathVariable Long id, @Valid @RequestBody UpdateReq r) { return service.update(id, me, r.content(), r.scheduledAt()); }

    @DeleteMapping("/api/scheduled/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Long me, @PathVariable Long id) { service.cancel(id, me); }
}