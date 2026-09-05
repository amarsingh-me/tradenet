package com.govtech.chat.chat_backend.dto;

import com.govtech.chat.chat_backend.entity.Message;
import java.time.Instant;

public record MessageResponse(Long id, Long fromId, Long toId, String content, Instant sentAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getFromId(),
                message.getToId(),
                message.getContent(),
                message.getSentAt());
    }
}
