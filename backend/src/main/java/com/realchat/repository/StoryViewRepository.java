package com.realchat.repository;

import com.realchat.domain.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StoryViewRepository extends JpaRepository<StoryView, StoryView.Key> {
    @Query("select v.storyId from StoryView v where v.viewerId = :viewer and v.storyId in :ids")
    List<Long> viewedIds(@Param("viewer") Long viewerId, @Param("ids") Collection<Long> storyIds);

    List<StoryView> findByStoryIdOrderByViewedAtDesc(Long storyId);

    @Query("select v.storyId, count(v) from StoryView v where v.storyId in :ids group by v.storyId")
    List<Object[]> counts(@Param("ids") Collection<Long> storyIds);

    /** Idempotent: opening a story twice (or two tabs at once) records one view. */
    @Transactional
    @Modifying
    @Query(value = "insert ignore into story_views (story_id, viewer_id, viewed_at) values (:s, :v, :now)", nativeQuery = true)
    int markViewed(@Param("s") Long storyId, @Param("v") Long viewerId, @Param("now") Instant now);
}