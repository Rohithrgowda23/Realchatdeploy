package com.realchat.repository;

import com.realchat.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long before, Pageable page);
    List<Notification> findByUserIdAndReadAtIsNullAndIdLessThanOrderByIdDesc(Long userId, Long before, Pageable page);
    long countByUserIdAndReadAtIsNull(Long userId);

    @Transactional @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :u and n.readAt is null")
    int markAllRead(@Param("u") Long userId, @Param("now") Instant now);

    @Transactional @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :u and n.readAt is null and n.id in :ids")
    int markRead(@Param("u") Long userId, @Param("ids") Collection<Long> ids, @Param("now") Instant now);

    @Transactional long deleteByIdAndUserId(Long id, Long userId);
    @Transactional long deleteByUserId(Long userId);

    @Transactional @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") Instant cutoff);
}