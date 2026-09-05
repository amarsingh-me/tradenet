package com.govtech.chat.chat_backend.config;

import com.govtech.chat.chat_backend.realtime.ChatWebSocketHandler;
import com.govtech.chat.chat_backend.web.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // setCreateSession(false): otherwise an anonymous handshake silently gets a fresh, empty
        // HttpSession instead of being rejected -- WebSocketAuthInterceptor (registered next)
        // relies on "no userId attribute" meaning "not logged in", not "session didn't exist yet".
        HttpSessionHandshakeInterceptor sessionInterceptor = new HttpSessionHandshakeInterceptor();
        sessionInterceptor.setCreateSession(false);

        // No CORS config exists anywhere else in this app (same-origin nginx/Vite proxying makes
        // it unnecessary for plain HTTP); allowedOrigins("*") keeps that same posture for the
        // handshake's Origin check rather than hardcoding a specific origin that would break
        // parity between the Docker Compose port and `npm run dev`'s port.
        registry
                .addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(sessionInterceptor, new WebSocketAuthInterceptor())
                .setAllowedOrigins("*");
    }
}
