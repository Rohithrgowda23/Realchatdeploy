package com.realchat.repository;

import com.realchat.domain.CallRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface CallRepository extends JpaRepository<CallRecord, Long> {
    /** Only calls the user took part in. */
    @Query("select c from CallRecord c where (c.callerId = :u or c.calleeId = :u) and c.id < :before order by c.id desc")
    List<CallRecord> history(@Param("u") Long userId, @Param("before") Long before, Pageable page);

    @Query("select count(c) from CallRecord c where c.calleeId = :u and c.status = 'MISSED' and c.calleeSeen = false")
    long unseenMissed(@Param("u") Long userId);

    @Transactional @Modifying
    @Query("update CallRecord c set c.calleeSeen = true where c.calleeId = :u and c.status = 'MISSED' and c.calleeSeen = false")
    int markSeen(@Param("u") Long userId);

    /** After a crash/restart no call can still be running: close any that were left open. */
    @Transactional @Modifying
    @Query("update CallRecord c set c.status = 'FAILED', c.endReason = 'server_restart', c.endedAt = :now where c.status in ('RINGING', 'ACTIVE')")
    int closeStale(@Param("now") Instant now);
}