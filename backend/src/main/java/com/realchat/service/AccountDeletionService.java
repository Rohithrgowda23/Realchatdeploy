package com.realchat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void deleteAccount(Long userId) {

        // Verify that the account exists.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );

        if (count == null || count == 0) {
            throw new IllegalArgumentException("Account not found");
        }

        // Remove personal records that reference the user.
        jdbcTemplate.update(
                "DELETE FROM saved_messages WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM hidden_messages WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM user_sessions WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM oauth_accounts WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM notification_prefs WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM story_views WHERE viewer_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM story_hidden WHERE user_id = ? OR hidden_user_id = ?",
                userId, userId);
        jdbcTemplate.update(
                "DELETE FROM story_settings WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM stories WHERE author_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM user_blocks WHERE blocker_id = ? OR blocked_id = ?",
                userId, userId);
        jdbcTemplate.update(
                "DELETE FROM message_reactions WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM message_mentions WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM group_audit_events WHERE actor_id = ? OR target_id = ?",
                userId, userId);
        jdbcTemplate.update(
                "DELETE FROM calls WHERE caller_id = ? OR callee_id = ?",
                userId, userId);
        jdbcTemplate.update(
                "DELETE FROM join_requests WHERE user_id = ? OR decided_by = ?",
                userId, userId);
        jdbcTemplate.update(
                "DELETE FROM group_invites WHERE created_by = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM scheduled_messages WHERE sender_id = ?", userId);

        // Remove references to messages authored by this account.
        jdbcTemplate.update(
                "UPDATE messages SET reply_to_id = NULL " +
                        "WHERE reply_to_id IN (SELECT id FROM messages WHERE sender_id = ?)",
                userId);

        jdbcTemplate.update(
                "UPDATE messages SET forwarded_from_id = NULL " +
                        "WHERE forwarded_from_id IN (SELECT id FROM messages WHERE sender_id = ?)",
                userId);

        // Delete messages authored by this account.
        jdbcTemplate.update(
                "DELETE FROM messages WHERE sender_id = ?", userId);

        // Remove this user's memberships.
        jdbcTemplate.update(
                "DELETE FROM conversation_members WHERE user_id = ?", userId);

        // Transfer conversation ownership to a remaining member.
        jdbcTemplate.update(
                """
                UPDATE conversations c
                SET created_by = (
                    SELECT cm.user_id
                    FROM conversation_members cm
                    WHERE cm.conversation_id = c.id
                    ORDER BY cm.id
                    LIMIT 1
                )
                WHERE c.created_by = ?
                  AND EXISTS (
                    SELECT 1
                    FROM conversation_members cm
                    WHERE cm.conversation_id = c.id
                  )
                """,
                userId
        );

        // Delete conversations with no remaining members.
        jdbcTemplate.update(
                """
                DELETE FROM conversations c
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM conversation_members cm
                    WHERE cm.conversation_id = c.id
                )
                AND c.created_by = ?
                """,
                userId
        );

        // Finally, delete the account.
        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?", userId);
    }
}
