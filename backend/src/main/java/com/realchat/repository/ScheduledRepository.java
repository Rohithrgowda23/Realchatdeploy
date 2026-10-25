package com.realchat.repository;

import com.realchat.domain.ScheduledMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ScheduledRepository extends JpaRepository<ScheduledMessage, Long> {
    List<ScheduledMessage> findTop100BySenderIdOrderByScheduledAtDesc(Long senderId);
    long countBySenderIdAndStatus(Long senderId, String status);

    @Query("select s.id from ScheduledMessage s where s.status = 'PENDING' and s.scheduledAt <= :now order by s.scheduledAt asc")
    List<Long> dueIds(@Param("now") Instant now, Pageable page);

    /** Atomic claim: only one scheduler (instance or overlapping run) can move a row PENDING -> SENDING. */
    @Transactional
    @Modifying
    @Query("update ScheduledMessage s set s.status = 'SENDING', s.claimedAt = :now, s.version = s.version + 1 where s.id = :id and s.status = 'PENDING' and s.scheduledAt <= :now")
    int claim(@Param("id") Long id, @Param("now") Instant now);

    /** A claimed row whose sender crashed before committing never sent anything (send + SENT are one transaction), so it is safe to retry. */
    @Transactional
    @Modifying
    @Query("update ScheduledMessage s set s.status = 'PENDING', s.claimedAt = null, s.version = s.version + 1 where s.status = 'SENDING' and s.claimedAt < :cutoff")
    int releaseStuck(@Param("cutoff") Instant cutoff);
}