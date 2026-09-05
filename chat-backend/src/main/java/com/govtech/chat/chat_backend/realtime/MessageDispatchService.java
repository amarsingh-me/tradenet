package com.govtech.chat.chat_backend.realtime;

import com.govtech.chat.chat_backend.dto.MessageResponse;
import com.govtech.chat.chat_backend.dto.WsServerFrame;
import com.govtech.chat.chat_backend.entity.Message;
import com.govtech.chat.chat_backend.repository.MessageRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/**
 * The hand-built core of this app: a message accepted by {@link ChatWebSocketHandler} is persisted
 * and pushed to whichever of the sender's and recipient's browser tabs are currently connected.
 *
 * <h2>Same-sender ordering: free, from the container</h2>
 *
 * Spring's WebSocket support delivers inbound frames for a given session to the handler
 * non-concurrently -- message N+1 on one connection is never delivered until message N's {@link
 * ChatWebSocketHandler#handleTextMessage} call has returned.
 *
 * <h2>Cross-sender DB-commit ordering: the {@code persistLock}</h2>
 *
 * {@code Message.id} uses {@code GenerationType.IDENTITY}: the INSERT (and id assignment) happens
 * inside {@code save()}, but the transaction commits at method exit. Two concurrent {@code
 * dispatch()} calls on different threads could therefore commit out of id order. Since the
 * catch-up cursor ({@code GET /api/messages/after/{id}}) is id-based, a client that saw the higher
 * id via live push before the lower id's delayed commit would never see that message again on
 * reconnect -- silent message loss, not just a display-ordering quirk. A lock held for the
 * duration of one {@code save()} call closes this. This is deliberately not "the queue, renamed":
 * it guards one INSERT, not a 1,000-item buffer drained by a dedicated thread, and it does not
 * wrap the network fan-out below -- a slow recipient connection can no longer stall unrelated
 * senders' persistence, which the old single global queue could.
 *
 * <h2>Concurrent writes to one outbound connection: {@link WebSocketConnectionRegistry}</h2>
 *
 * {@code WebSocketSession.sendMessage()} isn't safe for concurrent multi-thread use, and two
 * different senders' dispatch calls can race to push to the same recipient. The registry wraps
 * every registered session in a {@code ConcurrentWebSocketSessionDecorator} to serialize sends and
 * bound how much it'll buffer for one slow client -- sharded backpressure, one bound per
 * connection, instead of one global valve.
 *
 * <h2>Admission control: the {@code dispatchPermits} semaphore</h2>
 *
 * The old queue's {@code offer()} + {@code MessageQueueFullException} -> 503 gave callers a fast,
 * explicit "busy, try again" signal under overload. Without it, and with {@code
 * spring.threads.virtual.enabled=true} removing Tomcat's thread-pool ceiling, a burst would be
 * accepted without limit and the failure would just move downstream to connection-pool contention
 * with no signal at all. The semaphore restores an explicit, bounded-concurrency reject valve
 * around the actual persist+push unit of work, without reintroducing a buffer or a dedicated
 * thread -- a caller that can't get a permit is told so immediately, over the same connection, via
 * an error frame.
 */
@Service
public class MessageDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatchService.class);

    // Sized around 2-3x HikariCP's default maximum-pool-size (10): enough concurrent in-flight
    // dispatches to absorb a burst without every one of them queuing on a DB connection with no
    // signal, but still a real, deliberately low ceiling rather than "as many as virtual threads
    // will let us start."
    private static final int DISPATCH_PERMITS = 30;

    private final MessageRepository messageRepository;
    private final WebSocketConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;
    private final Semaphore dispatchPermits;
    private final Object persistLock = new Object();

    // Explicitly @Autowired because a second (package-private, test-only) constructor exists
    // below -- with more than one constructor present, Spring won't pick one implicitly.
    @Autowired
    public MessageDispatchService(
            MessageRepository messageRepository,
            WebSocketConnectionRegistry connectionRegistry,
            ObjectMapper objectMapper
    ) {
        this(messageRepository, connectionRegistry, objectMapper, DISPATCH_PERMITS);
    }

    // Package-private: lets tests exercise admission-control rejection with a small permit count
    // instead of needing to hold open 30 concurrent in-flight dispatches to prove the behavior.
    MessageDispatchService(
            MessageRepository messageRepository,
            WebSocketConnectionRegistry connectionRegistry,
            ObjectMapper objectMapper,
            int dispatchPermits
    ) {
        this.messageRepository = messageRepository;
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
        this.dispatchPermits = new Semaphore(dispatchPermits);
    }

    /** Persists and delivers one message. Called synchronously from {@link ChatWebSocketHandler}. */
    public void dispatch(Long fromId, Long toId, String content) {
        if (!dispatchPermits.tryAcquire()) {
            log.warn("Dispatch permits exhausted, rejecting message from {}", fromId);
            send(fromId, WsServerFrame.error("Server is busy, try again shortly"));
            return;
        }
        try {
            Message saved;
            synchronized (persistLock) {
                saved = messageRepository.save(new Message(fromId, toId, content, Instant.now()));
            }
            MessageResponse payload = MessageResponse.from(saved);
            pushTo(toId, payload);
            // Echo back to the sender's own connections too: the frontend deliberately does not
            // optimistically render a locally-built message, so that what's shown is always
            // exactly what the server persisted, not a client guess that could drift from it.
            pushTo(fromId, payload);
        } finally {
            dispatchPermits.release();
        }
    }

    private void pushTo(Long userId, MessageResponse payload) {
        send(userId, WsServerFrame.message(payload));
    }

    private void send(Long userId, WsServerFrame frame) {
        String json = objectMapper.writeValueAsString(frame);
        for (WebSocketSession session : connectionRegistry.sessionsFor(userId)) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException | IllegalStateException e) {
                // The write failed, meaning this connection is dead (closed tab, dropped network,
                // or the per-connection ConcurrentWebSocketSessionDecorator gave up on a slow
                // client). Remove it so future pushes don't keep paying the cost of a doomed
                // write, and so it doesn't leak forever if afterConnectionClosed/handleTransportError
                // didn't already fire for it.
                connectionRegistry.deregister(userId, session);
            }
        }
    }
}
