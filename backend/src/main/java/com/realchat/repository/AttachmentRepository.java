package com.realchat.repository;

import com.realchat.domain.Attachment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    Optional<Attachment> findByMessageId(Long messageId);
    List<Attachment> findByMessageIdIn(Collection<Long> messageIds);
    boolean existsByStoredName(String storedName);

    /** Gallery: only this conversation, not deleted, not hidden by the caller; newest first, cursor = message id. */
    @Query("select a from Attachment a where a.conversationId = :c and a.kind in :kinds and a.messageId < :before "
            + "and a.messageId in (select m.id from ChatMessage m where m.conversationId = :c and m.deleted = false "
            + "and m.id not in (select h.messageId from HiddenMessage h where h.userId = :u)) order by a.messageId desc")
    List<Attachment> gallery(@Param("c") Long conversationId, @Param("kinds") Collection<String> kinds,
                             @Param("before") Long before, @Param("u") Long userId, Pageable page);
}