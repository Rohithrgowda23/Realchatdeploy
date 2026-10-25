package com.realchat.repository;

import com.realchat.domain.StoryHidden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StoryHiddenRepository extends JpaRepository<StoryHidden, Long> {
    List<StoryHidden> findByUserId(Long userId);
    boolean existsByUserIdAndHiddenUserId(Long userId, Long hiddenUserId);
    @Transactional long deleteByUserId(Long userId);
}