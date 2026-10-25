package com.realchat.web;

import com.realchat.service.InviteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InviteController {
    public record CreateReq(Integer expiresInHours, Integer maxUses, boolean requiresApproval) {}
    /** The token travels in the body, never in a URL, so it cannot end up in access logs. */
    public record TokenReq(@NotBlank @Size(max = 200) String token) {}

    private final InviteService service;

    @PostMapping("/api/conversations/{id}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteService.CreatedInvite create(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestBody CreateReq r) {
        return service.create(id, me, r.expiresInHours(), r.maxUses(), r.requiresApproval());
    }

    @GetMapping("/api/conversations/{id}/invites")
    public List<InviteService.InviteView> list(@AuthenticationPrincipal Long me, @PathVariable Long id) { return service.list(id, me); }

    @DeleteMapping("/api/conversations/{id}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Long me, @PathVariable Long id, @PathVariable Long inviteId) { service.revoke(id, me, inviteId); }

    @PostMapping("/api/invites/preview")
    public InviteService.InvitePreview preview(@AuthenticationPrincipal Long me, @Valid @RequestBody TokenReq r) { return service.preview(r.token(), me); }

    @PostMapping("/api/invites/join")
    public InviteService.JoinResult join(@AuthenticationPrincipal Long me, @Valid @RequestBody TokenReq r) { return service.join(r.token(), me); }

    @GetMapping("/api/conversations/{id}/join-requests")
    public List<InviteService.JoinRequestView> pending(@AuthenticationPrincipal Long me, @PathVariable Long id) { return service.pending(id, me); }

    @PostMapping("/api/conversations/{id}/join-requests/{reqId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal Long me, @PathVariable Long id, @PathVariable Long reqId) { service.decide(id, me, reqId, true); }

    @PostMapping("/api/conversations/{id}/join-requests/{reqId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal Long me, @PathVariable Long id, @PathVariable Long reqId) { service.decide(id, me, reqId, false); }

    @GetMapping("/api/join-requests/mine")
    public List<InviteService.MyRequest> mine(@AuthenticationPrincipal Long me) { return service.mine(me); }

    @DeleteMapping("/api/join-requests/{reqId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Long me, @PathVariable Long reqId) { service.cancel(reqId, me); }
}