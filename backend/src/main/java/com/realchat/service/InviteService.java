package com.realchat.service;

import com.realchat.domain.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Secure group invitations and join requests. Tokens are 256-bit random, only their SHA-256 hash is stored, they can
 * expire, be limited in uses and be revoked, and holding one never grants anything above plain MEMBER. Admin decisions
 * re-check the actor's CURRENT role and apply at most once.
 */
@Service
public class InviteService {
    public record CreatedInvite(Long id, String token, Instant expiresAt, Integer maxUses, boolean requiresApproval) {}
    public record InviteView(Long id, String createdBy, Instant createdAt, Instant expiresAt, Integer maxUses, int useCount, boolean requiresApproval, String status) {}
    public record InvitePreview(String groupName, String description, String avatarUrl, int memberCount, boolean requiresApproval, boolean alreadyMember, boolean requestPending) {}
    public record JoinResult(String status, Long conversationId) {}
    public record JoinRequestView(Long id, Long userId, String userName, String userAvatar, Instant createdAt) {}
    public record MyRequest(Long id, Long conversationId, String groupName, Instant createdAt) {}

    private final InviteRepository invites;
    private final JoinRequestRepository requests;
    private final MemberRepository members;
    private final ConversationRepository convs;
    private final UserRepository users;
    private final ChatService chat;
    private final RateLimitService limiter;
    private final NotificationService notifications;
    private final SecureRandom random = new SecureRandom();

    public InviteService(InviteRepository invites, JoinRequestRepository requests, MemberRepository members, ConversationRepository convs,
                         UserRepository users, ChatService chat, RateLimitService limiter, NotificationService notifications) {
        this.invites = invites; this.requests = requests; this.members = members; this.convs = convs; this.users = users; this.chat = chat; this.limiter = limiter; this.notifications = notifications;
    }

    private static ApiException invalid() { return ApiException.notFound("This invite is invalid or has expired"); }

    private static boolean canManage(ConversationMember m) { return "OWNER".equals(m.getRole()) || "ADMIN".equals(m.getRole()); }

    /** Current membership and role are read from the database on every call. */
    private ConversationMember requireManager(Long convId, Long me) {
        ConversationMember m = members.findByConversationIdAndUserId(convId, me).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!canManage(m)) throw ApiException.forbidden("Only owners and admins can do this");
        return m;
    }

    private Conversation requireGroup(Long convId) {
        return convs.findById(convId).filter(c -> "GROUP".equals(c.getType())).orElseThrow(() -> ApiException.notFound("Group not found"));
    }

    // ---------------------------------------------------------------- invites
    @Transactional
    public CreatedInvite create(Long convId, Long me, Integer expiresInHours, Integer maxUses, boolean requiresApproval) {
        requireManager(convId, me);
        requireGroup(convId);
        limiter.check("invite:create:" + me, 20, Duration.ofHours(1));
        if (expiresInHours != null && (expiresInHours < 1 || expiresInHours > 24 * 90)) throw ApiException.bad("Expiry must be between 1 hour and 90 days");
        if (maxUses != null && (maxUses < 1 || maxUses > 1000)) throw ApiException.bad("Uses must be between 1 and 1000");
        byte[] b = new byte[32];
        random.nextBytes(b);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        GroupInvite i = new GroupInvite();
        i.setConversationId(convId); i.setCreatedBy(me); i.setTokenHash(PasswordResetService.sha256(raw));
        i.setExpiresAt(expiresInHours == null ? null : Instant.now().plus(Duration.ofHours(expiresInHours)));
        i.setMaxUses(maxUses); i.setRequiresApproval(requiresApproval);
        i = invites.save(i);
        chat.audit(convId, me, "INVITE_CREATED", null, null);
        return new CreatedInvite(i.getId(), raw, i.getExpiresAt(), i.getMaxUses(), i.isRequiresApproval()); // the only time the token is visible
    }

    @Transactional(readOnly = true)
    public List<InviteView> list(Long convId, Long me) {
        requireManager(convId, me);
        List<GroupInvite> all = invites.findByConversationIdOrderByIdDesc(convId);
        Map<Long, String> names = users.findAllById(all.stream().map(GroupInvite::getCreatedBy).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, User::getName));
        Instant now = Instant.now();
        return all.stream().map(i -> {
            String st = i.isRevoked() ? "REVOKED" : (i.getExpiresAt() != null && !i.getExpiresAt().isAfter(now)) ? "EXPIRED"
                    : (i.getMaxUses() != null && i.getUseCount() >= i.getMaxUses()) ? "USED_UP" : "ACTIVE";
            return new InviteView(i.getId(), names.getOrDefault(i.getCreatedBy(), "Former user"), i.getCreatedAt(), i.getExpiresAt(), i.getMaxUses(), i.getUseCount(), i.isRequiresApproval(), st);
        }).toList();
    }

    @Transactional
    public void revoke(Long convId, Long me, Long inviteId) {
        requireManager(convId, me);
        GroupInvite i = invites.findById(inviteId).filter(x -> x.getConversationId().equals(convId)).orElseThrow(() -> ApiException.notFound("Invite not found"));
        if (i.isRevoked()) return;
        i.setRevoked(true);
        invites.save(i);
        requests.rejectPendingForInvite(inviteId, Instant.now()); // requests that came through a revoked link no longer stand
        chat.audit(convId, me, "INVITE_REVOKED", null, null);
    }

    private GroupInvite validInvite(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) throw invalid();
        GroupInvite i = invites.findByTokenHash(PasswordResetService.sha256(token)).orElseThrow(InviteService::invalid);
        Instant now = Instant.now();
        if (i.isRevoked() || (i.getExpiresAt() != null && !i.getExpiresAt().isAfter(now))
                || (i.getMaxUses() != null && i.getUseCount() >= i.getMaxUses())) throw invalid();
        return i;
    }

    // ---------------------------------------------------------------- joining
    @Transactional(readOnly = true)
    public InvitePreview preview(String token, Long me) {
        limiter.check("invite:use:" + me, 60, Duration.ofHours(1));
        GroupInvite inv = validInvite(token);
        Conversation c = convs.findById(inv.getConversationId()).filter(x -> "GROUP".equals(x.getType())).orElseThrow(InviteService::invalid);
        boolean member = members.findByConversationIdAndUserId(c.getId(), me).isPresent();
        boolean pending = requests.existsByConversationIdAndUserIdAndStatus(c.getId(), me, "PENDING");
        return new InvitePreview(c.getName(), c.getDescription(), c.getAvatarUrl(), members.memberIds(c.getId()).size(), inv.isRequiresApproval(), member, pending);
    }

    @Transactional
    public JoinResult join(String token, Long me) {
        limiter.check("invite:use:" + me, 60, Duration.ofHours(1));
        GroupInvite inv = validInvite(token);
        Conversation c = convs.findById(inv.getConversationId()).filter(x -> "GROUP".equals(x.getType())).orElseThrow(InviteService::invalid);
        if (members.findByConversationIdAndUserId(c.getId(), me).isPresent()) return new JoinResult("ALREADY_MEMBER", c.getId());

        if (inv.isRequiresApproval()) {
            if (requests.existsByConversationIdAndUserIdAndStatus(c.getId(), me, "PENDING")) return new JoinResult("PENDING", c.getId());
            if (invites.consume(inv.getId(), Instant.now()) != 1) throw invalid();
            JoinRequest r = new JoinRequest();
            r.setConversationId(c.getId()); r.setUserId(me); r.setInviteId(inv.getId()); r.setStatus("PENDING"); r.setPendingKey(1);
            requests.save(r);
            String name = users.findById(me).map(User::getName).orElse("Someone");
            List<Long> managers = members.findByConversationId(c.getId()).stream().filter(InviteService::canManage).map(ConversationMember::getUserId).toList();
            chat.broadcast(managers, "JOIN_REQUEST", Map.of("conversationId", c.getId(), "groupName", c.getName(), "userName", name));
            managers.forEach(uid -> notifications.notify(uid, "JOIN_REQUEST", name + " wants to join " + c.getName(), null, c.getId(), null));
            return new JoinResult("PENDING", c.getId());
        }
        if (invites.consume(inv.getId(), Instant.now()) != 1) throw invalid();
        chat.addMember(c.getId(), me, "MEMBER"); // possession of a link never makes anyone an admin
        chat.audit(c.getId(), me, "MEMBER_JOINED", null, "invite");
        chat.broadcast(members.memberIds(c.getId()), "CONVERSATION_UPDATED", Map.of("conversationId", c.getId()));
        return new JoinResult("JOINED", c.getId());
    }

    // ---------------------------------------------------------------- join requests
    @Transactional(readOnly = true)
    public List<JoinRequestView> pending(Long convId, Long me) {
        requireManager(convId, me);
        List<JoinRequest> list = requests.findByConversationIdAndStatusOrderByIdAsc(convId, "PENDING");
        Map<Long, User> byId = users.findAllById(list.stream().map(JoinRequest::getUserId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().filter(r -> byId.containsKey(r.getUserId()))
                .map(r -> new JoinRequestView(r.getId(), r.getUserId(), byId.get(r.getUserId()).getName(), byId.get(r.getUserId()).getAvatarUrl(), r.getCreatedAt())).toList();
    }

    @Transactional
    public void decide(Long convId, Long me, Long requestId, boolean approve) {
        requireManager(convId, me); // role re-read now, not trusted from when the page was loaded
        Conversation c = requireGroup(convId);
        JoinRequest r = requests.findById(requestId).filter(x -> x.getConversationId().equals(convId)).orElseThrow(() -> ApiException.notFound("Request not found"));
        boolean userStillExists = users.findById(r.getUserId()).isPresent();
        boolean doApprove = approve && userStillExists;
        if (requests.decide(requestId, doApprove ? "APPROVED" : "REJECTED", me, Instant.now()) != 1) throw ApiException.conflict("This request was already handled");
        if (doApprove) {
            if (members.findByConversationIdAndUserId(convId, r.getUserId()).isEmpty()) chat.addMember(convId, r.getUserId(), "MEMBER");
            chat.audit(convId, me, "JOIN_APPROVED", r.getUserId(), null);
            chat.broadcast(members.memberIds(convId), "CONVERSATION_UPDATED", Map.of("conversationId", convId));
        } else {
            chat.audit(convId, me, "JOIN_REJECTED", r.getUserId(), null);
        }
        chat.broadcast(List.of(r.getUserId()), "JOIN_DECISION", Map.of("conversationId", convId, "groupName", c.getName(), "approved", doApprove));
        notifications.notify(r.getUserId(), "JOIN_DECISION", doApprove ? "You were added to " + c.getName() : "Your request to join " + c.getName() + " was declined", null, doApprove ? convId : null, null);
    }

    @Transactional
    public void cancel(Long requestId, Long me) {
        JoinRequest r = requests.findById(requestId).filter(x -> x.getUserId().equals(me)).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (requests.decide(r.getId(), "CANCELLED", me, Instant.now()) != 1) throw ApiException.conflict("This request was already handled");
    }

    @Transactional(readOnly = true)
    public List<MyRequest> mine(Long me) {
        List<JoinRequest> list = requests.findByUserIdAndStatusOrderByIdDesc(me, "PENDING");
        Map<Long, String> names = convs.findAllById(list.stream().map(JoinRequest::getConversationId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Conversation::getId, x -> x.getName() == null ? "Group" : x.getName()));
        return list.stream().map(r -> new MyRequest(r.getId(), r.getConversationId(), names.getOrDefault(r.getConversationId(), "Group"), r.getCreatedAt())).toList();
    }
}