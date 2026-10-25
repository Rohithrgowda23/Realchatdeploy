package com.realchat.repository;

import com.realchat.domain.ChatMessage;
import com.realchat.domain.MessageMention;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MentionRepository extends JpaRepository<MessageMention, Long> {
    List<MessageMention> findByMessageIdIn(Collection<Long> messageIds);

    @Query("select count(m) from ChatMessage m where m.conversationId = :c and m.id > :after and m.senderId <> :u and m.deleted = false "
            + "and m.id in (select x.messageId from MessageMention x where x.userId = :u)")
    long countUnreadMentions(@Param("c") Long conversationId, @Param("after") long after, @Param("u") Long userId);

    @Query("select m from ChatMessage m where m.conversationId = :c and m.id < :before and m.deleted = false "
            + "and m.id in (select x.messageId from MessageMention x where x.userId = :u) "
            + "and m.id not in (select h.messageId from HiddenMessage h where h.userId = :u) order by m.id desc")
    List<ChatMessage> mentioning(@Param("c") Long conversationId, @Param("u") Long userId, @Param("before") Long before, Pageable page);
}