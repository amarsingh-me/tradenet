package com.govtech.chat.chat_backend.repository;

import com.govtech.chat.chat_backend.entity.Message;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Ordered by id (see Message's javadoc-style comment) rather than sentAt, so history replays
    // in true write order even if two messages land in the same millisecond.
    @Query("""
                select m from Message m
                    where (m.fromId = :userA and m.toId = :userB)
                        or (m.fromId = :userB and m.toId = :userA)
                    order by m.id asc
            """
    )
    List<Message> findConversation(@Param("userA") Long userA, @Param("userB") Long userB);

    // Scoped to the session user (both directions) rather than trusting a client-supplied from_id,
    // unlike the diagram's literal /get-all-messages-after — a client could otherwise pass any
    // user id and read someone else's inbox.
    @Query("""
                select m from Message m
                    where (m.fromId = :userId or m.toId = :userId) and m.id > :afterId
                    order by m.id asc
            """
    )
    List<Message> findAllForUserAfter(@Param("userId") Long userId, @Param("afterId") Long afterId);
}
