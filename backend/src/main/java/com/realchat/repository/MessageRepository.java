package com.realchat.repository;

import com.realchat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("select m from ChatMessage m where m.conversationId = :c and m.id < :before "
         + "and m.id not in (select h.messageId from HiddenMessage h where h.userId = :u) order by m.id desc")
    List<ChatMessage> page(@Param("c") Long conversationId, @Param("before") Long before, @Param("u") Long userId, Pageable page);

    Optional<ChatMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    @Query("select count(m) from ChatMessage m where m.conversationId = :c and m.id > :after and m.senderId <> :u and m.deleted = false")
    long countUnread(@Param("c") Long conversationId, @Param("after") long after, @Param("u") Long userId);

    @Query("select max(m.id) from ChatMessage m where m.conversationId = :c")
    Long maxId(@Param("c") Long conversationId);

    /** locate() is a plain substring test, so %, _ and quotes in the query text have no special meaning. */
    @Query("select m from ChatMessage m where m.conversationId = :c and m.deleted = false "
         + "and m.id not in (select h.messageId from HiddenMessage h where h.userId = :u) "
         + "and locate(lower(:q), lower(m.content)) > 0 order by m.id desc")
    List<ChatMessage> search(@Param("c") Long conversationId, @Param("q") String q, @Param("u") Long userId, Pageable page);

    /** Cross-conversation search. Only conversations the user is CURRENTLY a member of are ever considered. cid/sid 0 = any. */
    @Query("select m from ChatMessage m where m.deleted = false "
         + "and m.conversationId in (select cm.conversationId from ConversationMember cm where cm.userId = :u) "
         + "and m.id not in (select h.messageId from HiddenMessage h where h.userId = :u) "
         + "and (:cid = 0L or m.conversationId = :cid) and (:sid = 0L or m.senderId = :sid) "
         + "and m.createdAt >= :from and m.createdAt < :to "
         + "and (:q = '' or locate(lower(:q), lower(m.content)) > 0) order by m.id desc")
    List<ChatMessage> searchAll(@Param("u") Long userId, @Param("q") String q, @Param("cid") Long cid, @Param("sid") Long sid,
                                @Param("from") Instant from, @Param("to") Instant to, Pageable page);
}
