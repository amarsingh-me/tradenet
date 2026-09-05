package com.govtech.chat.chat_backend.service;

import com.govtech.chat.chat_backend.dto.MessageResponse;
import com.govtech.chat.chat_backend.repository.MessageRepository;
import java.util.List;
import org.springframework.stereotype.Service;

// Covers only the read paths. The write path's actual logic (queueing, ordering, persist-then-
// push) lives in MessageDispatchService, which is the real service for this feature -- this
// class exists purely so the controller's read endpoints go through a service layer too, for
// consistency, even though today they're 1:1 repository pass-throughs plus entity-to-DTO
// mapping, so the controller never touches the entity type.
@Service
public class MessageQueryService {

    private final MessageRepository messageRepository;

    public MessageQueryService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public List<MessageResponse> conversation(Long selfId, Long otherUserId) {
        return messageRepository.findConversation(selfId, otherUserId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    public List<MessageResponse> messagesAfter(Long selfId, Long messageId) {
        return messageRepository.findAllForUserAfter(selfId, messageId).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
