package com.realchat.web;

import com.realchat.exception.ApiException;
import com.realchat.service.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/** Signalling entry points. The acting user is always the authenticated principal; ids in payloads only name the call. */
@Controller
@RequiredArgsConstructor
public class CallWsController {
    public record StartP(Long conversationId, String type) {}
    public record IdP(String callId) {}
    public record EndP(String callId, boolean failed) {}
    public record SignalP(String callId, String kind, String payload) {}

    private final CallService calls;

    private static Long me(Principal p) { return Long.valueOf(p.getName()); }

    @MessageMapping("/call.start") public void start(StartP p, Principal who) { calls.start(me(who), p.conversationId(), p.type()); }
    @MessageMapping("/call.accept") public void accept(IdP p, Principal who) { calls.accept(me(who), p.callId()); }
    @MessageMapping("/call.reject") public void reject(IdP p, Principal who) { calls.reject(me(who), p.callId()); }
    @MessageMapping("/call.cancel") public void cancel(IdP p, Principal who) { calls.cancel(me(who), p.callId()); }
    @MessageMapping("/call.end") public void end(EndP p, Principal who) { calls.end(me(who), p.callId(), p.failed()); }
    @MessageMapping("/call.signal") public void signal(SignalP p, Principal who) { calls.signal(me(who), p.callId(), p.kind(), p.payload()); }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> onError(Exception e) { return Map.of("message", e instanceof ApiException ? e.getMessage() : "Call request failed"); }
}