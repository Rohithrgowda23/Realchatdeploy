package com.realchat.repository;

import com.realchat.domain.HiddenMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HiddenRepository extends JpaRepository<HiddenMessage, Long> {
    boolean existsByUserIdAndMessageId(Long userId, Long messageId);
}
