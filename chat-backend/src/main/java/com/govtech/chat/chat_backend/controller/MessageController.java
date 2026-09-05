package com.govtech.chat.chat_backend.controller;

import com.govtech.chat.chat_backend.dto.MessageResponse;
import com.govtech.chat.chat_backend.service.MessageQueryService;
import com.govtech.chat.chat_backend.web.SessionUser;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageQueryService messageQueryService;

    public MessageController(MessageQueryService messageQueryService) {
        this.messageQueryService = messageQueryService;
    }

    @GetMapping("/messages/{otherUserId}")
    public List<MessageResponse> conversation(
            @PathVariable Long otherUserId, HttpSession session) {
        Long selfId = SessionUser.requireUserId(session);
        return messageQueryService.conversation(selfId, otherUserId);
    }

    @GetMapping("/messages/after/{messageId}")
    public List<MessageResponse> messagesAfter(
            @PathVariable Long messageId, HttpSession session) {
        Long selfId = SessionUser.requireUserId(session);
        return messageQueryService.messagesAfter(selfId, messageId);
    }
}
