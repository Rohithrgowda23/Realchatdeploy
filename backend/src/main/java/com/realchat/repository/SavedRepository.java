package com.realchat.repository;

import com.realchat.domain.ChatMessage;
import com.realchat.domain.SavedMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedRepository extends JpaRepository<SavedMessage, Long> {
    Optional<SavedMessage> findByUserIdAndMessageId(Long userId, Long messageId);

    @Query("select s.messageId from SavedMessage s where s.userId = :u")
    List<Long> messageIds(@Param("u") Long userId);

    /** Access is re-checked on every read: still a member, not deleted, not hidden by this user. */
    @Query("select m from ChatMessage m, SavedMessage s where s.userId = :u and s.messageId = m.id and m.deleted = false "
         + "and m.conversationId in (select cm.conversationId from ConversationMember cm where cm.userId = :u) "
         + "and m.id not in (select h.messageId from HiddenMessage h where h.userId = :u) "
         + "and (:q = '' or locate(lower(:q), lower(m.content)) > 0) order by s.id desc")
    List<ChatMessage> saved(@Param("u") Long userId, @Param("q") String q, Pageable page);
}
