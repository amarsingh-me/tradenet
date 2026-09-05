package com.govtech.chat.chat_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// id is the DB auto-increment PK and doubles as the ordering/catch-up cursor. Deliberately NOT
// ordering by sentAt: client clocks (and even server clocks across a restart) can skew or go
// backwards, but IDENTITY is monotonically assigned by the single writer thread in
// MessageDispatchService, so it's the one value guaranteed to reflect true write order.
@Entity
@Table(
        name = "messages",
        indexes = {
            @Index(name = "idx_messages_from_to", columnList = "fromId,toId"),
            @Index(name = "idx_messages_to_from", columnList = "toId,fromId")
        })
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fromId;

    @Column(nullable = false)
    private Long toId;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private Instant sentAt;

    public Message(Long fromId, Long toId, String content, Instant sentAt) {
        this.fromId = fromId;
        this.toId = toId;
        this.content = content;
        this.sentAt = sentAt;
    }
}
