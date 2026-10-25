# Database

The schema is owned by **Flyway**: `backend/src/main/resources/db/migration/V1__init.sql`. It runs automatically when
the backend starts. Hibernate runs with `ddl-auto: validate`, so nothing is ever dropped or auto-altered; existing data
survives restarts. Add changes as new `V2__*.sql` files, never by editing an applied migration.

| Table | Purpose |
|---|---|
| `users` | accounts (BCrypt hash, unique email) |
| `conversations` | `DIRECT` or `GROUP`; `direct_key` (`minId_maxId`, UNIQUE) prevents duplicate direct chats |
| `conversation_members` | membership, role (`OWNER/ADMIN/MEMBER`), pinned/muted/archived, `last_read_message_id` (drives unread counts and read ticks) |
| `messages` | content, reply link, edited/deleted flags; index `(conversation_id, id)` for pagination |
| `message_reactions` | one row per (message, user, emoji) |
| `user_blocks` | blocker -> blocked |

Deleting a conversation cascades to members, messages and reactions.

V2 adds: `saved_messages`, `hidden_messages` (delete-for-me), `group_audit_events`, `user_sessions`, plus
`messages.forwarded_from_*` and `conversations.announcement_only`.
