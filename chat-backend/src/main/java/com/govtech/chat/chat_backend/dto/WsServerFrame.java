package com.govtech.chat.chat_backend.dto;

import java.util.Map;

public record WsServerFrame(String type, Object payload) {

    public static WsServerFrame message(MessageResponse message) {
        return new WsServerFrame("message", message);
    }

    public static WsServerFrame error(String reason) {
        return new WsServerFrame("error", Map.of("reason", reason));
    }
}
