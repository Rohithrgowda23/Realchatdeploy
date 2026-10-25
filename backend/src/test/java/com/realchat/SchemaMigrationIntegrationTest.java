package com.realchat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@MySqlIntegrationTest
class SchemaMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test void everyFlywayMigrationAppliedSuccessfully() {
        Integer failed = jdbc.queryForObject("select count(*) from flyway_schema_history where success = 0", Integer.class);
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where version is not null", Integer.class);
        assertEquals(0, failed);
        assertTrue(applied >= 11, "expected migrations V1..V11, found " + applied);
    }

    @Test void oauthOnlyUsersMayHaveNoPassword() {
        String nullable = jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema = database() and table_name = 'users' and column_name = 'password_hash'", String.class);
        assertEquals("YES", nullable);
    }

    @Test void databaseRefusesDuplicateDirectConversationsAndDuplicateEmails() {
        jdbc.update("insert into users (name, email, password_hash, created_at) values ('A', ?, 'x', now(3))", "a-" + System.nanoTime() + "@example.test");
        Long u = jdbc.queryForObject("select max(id) from users", Long.class);
        String key = "k-" + System.nanoTime();
        jdbc.update("insert into conversations (type, direct_key, created_by, created_at) values ('DIRECT', ?, ?, now(3))", key, u);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into conversations (type, direct_key, created_by, created_at) values ('DIRECT', ?, ?, now(3))", key, u));
        String email = "dup-" + System.nanoTime() + "@example.test";
        jdbc.update("insert into users (name, email, password_hash, created_at) values ('B', ?, 'x', now(3))", email);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into users (name, email, password_hash, created_at) values ('C', ?, 'x', now(3))", email));
    }

    @Test void deletingAConversationCascadesToItsMessages() {
        jdbc.update("insert into users (name, email, password_hash, created_at) values ('D', ?, 'x', now(3))", "d-" + System.nanoTime() + "@example.test");
        Long u = jdbc.queryForObject("select max(id) from users", Long.class);
        jdbc.update("insert into conversations (type, name, created_by, created_at) values ('GROUP', 'g', ?, now(3))", u);
        Long c = jdbc.queryForObject("select max(id) from conversations", Long.class);
        jdbc.update("insert into messages (conversation_id, sender_id, content, created_at) values (?, ?, 'hi', now(3))", c, u);
        jdbc.update("delete from conversations where id = ?", c);
        assertEquals(0, jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class, c));
    }

    @Test void callsTableRejectsDuplicateCallIds() {
        jdbc.update("insert into users (name, email, password_hash, created_at) values ('E', ?, 'x', now(3))", "e-" + System.nanoTime() + "@example.test");
        Long u = jdbc.queryForObject("select max(id) from users", Long.class);
        jdbc.update("insert into conversations (type, direct_key, created_by, created_at) values ('DIRECT', ?, ?, now(3))", "c-" + System.nanoTime(), u);
        Long c = jdbc.queryForObject("select max(id) from conversations", Long.class);
        String uid = java.util.UUID.randomUUID().toString();
        String sql = "insert into calls (call_uid, conversation_id, caller_id, callee_id, type, status, started_at) values (?, ?, ?, ?, 'AUDIO', 'RINGING', now(3))";
        jdbc.update(sql, uid, c, u, u);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, uid, c, u, u));
    }
}