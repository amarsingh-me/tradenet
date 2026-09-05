package com.govtech.chat.chat_backend.realtime;

import com.govtech.chat.chat_backend.dto.SendMessageRequest;
import com.govtech.chat.chat_backend.dto.WsServerFrame;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final WebSocketConnectionRegistry connectionRegistry;
    private final MessageDispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ChatWebSocketHandler(
            WebSocketConnectionRegistry connectionRegistry,
            MessageDispatchService dispatchService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.connectionRegistry = connectionRegistry;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Long userId = userIdOf(session);
        if (userId == null) {
            // Belt-and-braces: WebSocketAuthInterceptor already rejects a handshake with no
            // authenticated user before the upgrade completes, so this should be unreachable.
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        connectionRegistry.register(userId, session);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        Long fromId = userIdOf(session);

        SendMessageRequest request;
        try {
            request = objectMapper.readValue(message.getPayload(), SendMessageRequest.class);
        } catch (RuntimeException e) {
            sendError(session, "Malformed message");
            return;
        }

        Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            sendError(session, violations.iterator().next().getMessage());
            return;
        }

        // A single bad/oversized frame must not take the connection down -- validation above
        // already rejected it with an error frame instead of ever reaching dispatch.
        dispatchService.dispatch(fromId, request.toId(), request.content());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        connectionRegistry.deregister(userIdOf(session), session);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.warn("WebSocket transport error, deregistering connection", exception);
        connectionRegistry.deregister(userIdOf(session), session);
    }

    private void sendError(WebSocketSession session, String reason) {
        try {
            session.sendMessage(new TextMessage(
                            objectMapper.writeValueAsString(WsServerFrame.error(reason))
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to send error frame, deregistering connection", e);
            connectionRegistry.deregister(userIdOf(session), session);
        }
    }

    private Long userIdOf(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }
}
