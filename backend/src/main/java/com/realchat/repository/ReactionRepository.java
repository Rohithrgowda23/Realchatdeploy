package com.realchat.repository;

import com.realchat.domain.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<MessageReaction, Long> {
    List<MessageReaction> findByMessageIdIn(Collection<Long> messageIds);
    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);
}
