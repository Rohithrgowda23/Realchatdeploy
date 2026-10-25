package com.realchat.repository;

import com.realchat.domain.Story;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {
    /** Expiry is judged against the server clock passed in, never a client value. */
    @Query("select s from Story s where s.authorId in :authors and s.expiresAt > :now order by s.createdAt asc")
    List<Story> active(@Param("authors") Collection<Long> authors, @Param("now") Instant now);

    long countByAuthorIdAndExpiresAtAfter(Long authorId, Instant now);

    @Query("select s from Story s where s.expiresAt <= :now order by s.expiresAt asc")
    List<Story> expired(@Param("now") Instant now, Pageable page);

    boolean existsByStoredName(String storedName);

    @Query("select distinct m2.userId from ConversationMember m1, ConversationMember m2, Conversation c "
            + "where m1.conversationId = c.id and m2.conversationId = c.id and c.type = 'DIRECT' and m1.userId = :me and m2.userId <> :me")
    List<Long> directPartners(@Param("me") Long me);

    @Query("select distinct m2.userId from ConversationMember m1, ConversationMember m2, Conversation c "
            + "where m1.conversationId = c.id and m2.conversationId = c.id and c.type = 'GROUP' and m1.userId = :me and m2.userId <> :me")
    List<Long> groupmates(@Param("me") Long me);

    @Query("select count(m1) from ConversationMember m1, ConversationMember m2, Conversation c "
            + "where m1.conversationId = c.id and m2.conversationId = c.id and c.type = 'GROUP' and m1.userId = :a and m2.userId = :b")
    long sharedGroups(@Param("a") Long a, @Param("b") Long b);
}