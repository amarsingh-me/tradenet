package com.govtech.chat.chat_backend.web;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Runs after {@code HttpSessionHandshakeInterceptor} has copied the {@code HttpSession}'s
 * attributes into the handshake attribute map. There's no MVC exception-handling pipeline
 * protecting a WebSocket handshake the way {@link GlobalExceptionHandler}/{@link
 * UnauthenticatedException} protect the REST endpoints, so an unauthenticated handshake has to be
 * rejected here, before the upgrade completes, rather than after.
 */
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (attributes.get(SessionUser.USER_ID_KEY) == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception
    ) {
        // No-op: nothing to clean up if the handshake failed before a session was ever registered.
    }
}
