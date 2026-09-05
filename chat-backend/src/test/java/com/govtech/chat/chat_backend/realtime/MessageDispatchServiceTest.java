package com.govtech.chat.chat_backend.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.govtech.chat.chat_backend.entity.Message;
import com.govtech.chat.chat_backend.repository.MessageRepository;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for the hand-built core of the app. Deliberately plain Mockito, no Spring context --
 * these need to be fast and deterministic, which a full {@code @SpringBootTest} would only
 * obscure. See the class-level notes on {@link MessageDispatchService} for what each mechanism
 * tested here replaced from the old queue design, and why.
 */
class MessageDispatchServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final WebSocketConnectionRegistry connectionRegistry =
            mock(WebSocketConnectionRegistry.class);
    private final AtomicLong idSequence = new AtomicLong(1);

    private MessageDispatchService serviceWithPermits(int permits) {
        // save() must return the argument with a fake generated id, mirroring what
        // GenerationType.IDENTITY would do on flush, since MessageResponse.from() reads getId().
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(
                        invocation -> {
                            Message m = invocation.getArgument(0);
                            m.setId(idSequence.getAndIncrement());
                            return m;
                        });
        return new MessageDispatchService(
                messageRepository, connectionRegistry, new ObjectMapper(), permits);
    }

    @Test
    void dispatch_persistsMessageAndDeliversToBothSenderAndRecipientConnections() throws IOException {
        MessageDispatchService service = serviceWithPermits(10);
        WebSocketSession recipientSession = mock(WebSocketSession.class);
        WebSocketSession senderSession = mock(WebSocketSession.class);
        when(connectionRegistry.sessionsFor(2L)).thenReturn(List.of(recipientSession));
        when(connectionRegistry.sessionsFor(1L)).thenReturn(List.of(senderSession));

        service.dispatch(1L, 2L, "hello");

        verify(messageRepository).save(any(Message.class));
        // Delivered to the recipient (the obvious case) AND echoed back to the sender's own
        // connection -- the frontend relies on this echo instead of optimistically rendering its
        // own copy, see MessageDispatchService's class-level notes.
        verify(recipientSession).sendMessage(any(TextMessage.class));
        verify(senderSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void dispatch_persistsEvenWhenRecipientHasNoLiveConnection() {
        MessageDispatchService service = serviceWithPermits(10);
        when(connectionRegistry.sessionsFor(any())).thenReturn(List.of());

        service.dispatch(1L, 2L, "are you there?");

        // Durability over delivery: an offline recipient must not cause the message to be dropped.
        // It's recoverable later via GET /api/messages/after.
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void dispatch_removesDeadSessionThatFailsToSend() throws IOException {
        MessageDispatchService service = serviceWithPermits(10);
        WebSocketSession deadSession = mock(WebSocketSession.class);
        org.mockito.Mockito.doThrow(new IOException("broken pipe"))
                .when(deadSession)
                .sendMessage(any(TextMessage.class));
        when(connectionRegistry.sessionsFor(2L)).thenReturn(List.of(deadSession));
        when(connectionRegistry.sessionsFor(1L)).thenReturn(List.of());

        service.dispatch(1L, 2L, "into the void");

        // A write failure means the connection is dead; it must be pruned from the registry so it
        // doesn't keep absorbing failed writes (or leak) for the rest of the process's life.
        verify(connectionRegistry).deregister(eq(2L), eq(deadSession));
    }

    @Test
    void dispatch_rejectsWithErrorFrameOncePermitsAreExhausted() throws Exception {
        // A capacity-1 permit pool, held open by one in-flight dispatch (via a save() that blocks
        // until released), lets a second concurrent dispatch deterministically observe "no
        // permit available" -- the modern, connection-scoped restatement of the old queue's
        // offer()/MessageQueueFullException backpressure.
        CountDownLatch firstDispatchHoldingPermit = new CountDownLatch(1);
        CountDownLatch releaseFirstDispatch = new CountDownLatch(1);
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(
                        invocation -> {
                            firstDispatchHoldingPermit.countDown();
                            releaseFirstDispatch.await(5, TimeUnit.SECONDS);
                            Message m = invocation.getArgument(0);
                            m.setId(idSequence.getAndIncrement());
                            return m;
                        });
        MessageDispatchService service =
                new MessageDispatchService(
                        messageRepository, connectionRegistry, new ObjectMapper(), 1);
        when(connectionRegistry.sessionsFor(any())).thenReturn(List.of());
        WebSocketSession busySenderSession = mock(WebSocketSession.class);
        when(connectionRegistry.sessionsFor(3L)).thenReturn(List.of(busySenderSession));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> service.dispatch(1L, 2L, "first"));
            assertThat(firstDispatchHoldingPermit.await(5, TimeUnit.SECONDS)).isTrue();

            service.dispatch(3L, 2L, "second, should be rejected");

            verify(busySenderSession).sendMessage(any(TextMessage.class));
            verify(messageRepository, org.mockito.Mockito.times(1)).save(any(Message.class));
        } finally {
            releaseFirstDispatch.countDown();
            executor.shutdown();
        }
    }

    @Test
    void dispatch_serializesConcurrentPersistCalls() throws InterruptedException {
        // Proves the persist step's synchronized lock actually excludes concurrent callers -- the
        // narrow replacement for the old single-consumer-thread's cross-sender DB-commit-order
        // guarantee (see MessageDispatchService's class-level notes on the IDENTITY-before-commit
        // race this closes).
        int callers = 8;
        MessageDispatchService service = serviceWithPermits(callers);
        when(connectionRegistry.sessionsFor(any())).thenReturn(List.of());
        AtomicBoolean inPersist = new AtomicBoolean(false);
        AtomicBoolean sawConcurrentEntry = new AtomicBoolean(false);
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(
                        invocation -> {
                            if (!inPersist.compareAndSet(false, true)) {
                                sawConcurrentEntry.set(true);
                            }
                            Thread.sleep(5);
                            Message m = invocation.getArgument(0);
                            m.setId(idSequence.getAndIncrement());
                            inPersist.set(false);
                            return m;
                        });

        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch done = new CountDownLatch(callers);
        for (int i = 0; i < callers; i++) {
            int n = i;
            executor.submit(
                    () -> {
                        service.dispatch(1L, 2L, "msg-" + n);
                        done.countDown();
                    });
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(sawConcurrentEntry).as("no two persist calls ever overlapped").isFalse();
    }
}
