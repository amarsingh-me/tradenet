package com.govtech.chat.chat_backend.realtime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * The hand-built connection registry behind "deliver to client": a plain in-memory map from user
 * id to that user's live WebSocket connections. This -- not {@link WebSocketSession} itself -- is
 * the part of "real-time delivery" that has to be built rather than pulled from a library:
 * {@code TextWebSocketHandler}/{@code WebSocketSession} only know how to hold one connection open
 * and write frames to it, they have no idea which user a connection belongs to or how to fan a
 * message out to the right one.
 *
 * <p>A user can have more than one live connection (multiple browser tabs/devices), hence a map
 * per user rather than a single session. Sessions are wrapped in {@link
 * ConcurrentWebSocketSessionDecorator} at registration time -- {@code WebSocketSession.sendMessage()}
 * is not safe for concurrent invocation from multiple threads, and two different senders' dispatch
 * calls can race to push to the same recipient session. The decorator serializes those sends and
 * enforces a bounded per-connection buffer/time limit, closing a session that falls too far behind
 * instead of letting one slow client back up memory without bound -- sharded backpressure, one
 * bound per connection, instead of the old single global queue.
 *
 * <p>Keyed internally by {@code session.getId()} (stable across the raw/decorated wrapper, since
 * the decorator delegates {@code getId()}) rather than object identity, so {@link
 * #deregister(Long, WebSocketSession)} is correct regardless of which wrapper instance is passed
 * in (e.g. the raw session from {@code afterConnectionClosed} vs. the decorated one stored here).
 */
@Component
public class WebSocketConnectionRegistry {

    // How long/how much to buffer for one slow client before giving up on its connection. Chosen
    // generously for a chat app's small JSON payloads: a legitimately slow mobile connection
    // should survive a burst, but a truly stuck client shouldn't hold a session (and the memory
    // behind it) open indefinitely.
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final Map<Long, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES
        );
        sessionsByUser
                .computeIfAbsent(userId, _ -> new ConcurrentHashMap<>())
                .put(session.getId(), decorated);
    }

    public void deregister(Long userId, WebSocketSession session) {
        sessionsByUser.computeIfPresent(
                userId,
                (_, sessions) -> {
                    sessions.remove(session.getId());
                    // Prune empty maps instead of leaving them forever, otherwise long-running
                    // deployments accumulate one empty map per user who ever connected.
                    return sessions.isEmpty() ? null : sessions;
                });
    }

    public Collection<WebSocketSession> sessionsFor(Long userId) {
        Map<String, WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? List.of() : List.copyOf(sessions.values());
    }
}
