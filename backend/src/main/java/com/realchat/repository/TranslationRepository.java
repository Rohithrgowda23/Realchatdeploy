package com.realchat.repository;

import com.realchat.domain.MessageTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface TranslationRepository extends JpaRepository<MessageTranslation, Long> {
    Optional<MessageTranslation> findByMessageIdAndTargetLang(Long messageId, String targetLang);
    @Transactional long deleteByMessageId(Long messageId);
}