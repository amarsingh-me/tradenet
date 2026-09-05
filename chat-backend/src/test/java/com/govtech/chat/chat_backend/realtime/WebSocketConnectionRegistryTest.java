package com.govtech.chat.chat_backend.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Cheap, high-signal proof that {@link WebSocketConnectionRegistry} actually wires every
 * registered session through {@link ConcurrentWebSocketSessionDecorator} -- a missing decorator
 * would otherwise only surface under real concurrent load (two senders racing to push to the same
 * recipient), never in a two-person manual demo. Serialization/backpressure behavior of the
 * decorator itself is Spring Framework's own tested concern, not re-tested here.
 */
class WebSocketConnectionRegistryTest {

    private final WebSocketConnectionRegistry registry = new WebSocketConnectionRegistry();

    @Test
    void register_wrapsSessionInConcurrentDecorator() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");

        registry.register(1L, session);

        Collection<WebSocketSession> stored = registry.sessionsFor(1L);
        assertThat(stored).hasSize(1);
        assertThat(stored.iterator().next()).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
    }

    @Test
    void deregister_removesSessionRegardlessOfWrapperInstance() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        registry.register(1L, session);

        // The raw session, not the decorated instance stored internally -- afterConnectionClosed
        // hands back the raw session, so deregister must match on the stable session id rather
        // than object identity.
        registry.deregister(1L, session);

        assertThat(registry.sessionsFor(1L)).isEmpty();
    }

    @Test
    void sessionsFor_returnsEmptyForUnknownUser() {
        assertThat(registry.sessionsFor(999L)).isEmpty();
    }
}
