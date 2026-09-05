package com.govtech.chat.chat_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reproduces the assessment's actual acceptance test ("simulated by opening the web app in 2
 * different browser windows and messaging each other") over a real WebSocket connection against a
 * running server, rather than MockMvc -- this is the one test that proves the whole hand-built
 * path (admission control -> persist lock -> connection-registry fan-out -> two independent
 * client connections) actually works end-to-end, not just each piece in isolation. Uses the JDK's
 * built-in {@link HttpClient#newWebSocketBuilder()} rather than a new test dependency.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RealtimeChatIntegrationTest {

    @LocalServerPort private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<WsPeer> peersToClose = new CopyOnWriteArrayList<>();

    @AfterEach
    void closePeers() {
        peersToClose.forEach(WsPeer::close);
    }

    @Test
    void twoUsers_messagingEachOther_bothReceiveTheMessageInRealTimeOverWebSocket() throws Exception {
        WsPeer alice = login("alice");
        WsPeer bob = login("bob");
        alice.connect();
        bob.connect();

        alice.send(bob.userId, "hello bob, this is alice");

        assertThat(bob.awaitPayload(5, TimeUnit.SECONDS))
                .as("recipient (bob) receives the message pushed over the WebSocket")
                .isTrue();
        assertThat(alice.awaitPayload(5, TimeUnit.SECONDS))
                .as("sender (alice) receives the echo of their own sent message")
                .isTrue();
        assertThat(bob.receivedContents()).containsExactly("hello bob, this is alice");
        assertThat(alice.receivedContents()).containsExactly("hello bob, this is alice");
    }

    private WsPeer login(String username) throws IOException, InterruptedException {
        WsPeer peer = new WsPeer(username);
        peersToClose.add(peer);
        peer.login();
        return peer;
    }

    private final class WsPeer {
        private final String username;
        private final HttpClient client = HttpClient.newHttpClient();
        private final List<String> receivedContents = new CopyOnWriteArrayList<>();
        private final CountDownLatch received = new CountDownLatch(1);
        private long userId;
        private String sessionCookie;
        private WebSocket socket;

        WsPeer(String username) {
            this.username = username;
        }

        void login() throws IOException, InterruptedException {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/api/login"))
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"username\":\"" + username + "\"}"))
                            .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            this.userId = body.get("id").asLong();
            // Explicitly capture and re-attach the session cookie on the WS handshake rather than
            // relying on an HttpClient-level CookieHandler to carry it along implicitly -- keeps
            // the test deterministic instead of depending on cookie-jar behavior across a plain
            // HTTP client and a WebSocket upgrade request.
            this.sessionCookie =
                    response
                            .headers()
                            .firstValue("set-cookie")
                            .map(header -> header.split(";", 2)[0])
                            .orElseThrow(() -> new IllegalStateException("Login did not set a session cookie"));
        }

        void connect() throws Exception {
            socket =
                    client.newWebSocketBuilder()
                            .header("Cookie", sessionCookie)
                            .buildAsync(URI.create(wsBaseUrl() + "/ws/chat"), new PeerListener())
                            .get(5, TimeUnit.SECONDS);
        }

        void send(long toId, String content) {
            String json =
                    objectMapper
                            .createObjectNode()
                            .put("toId", toId)
                            .put("content", content)
                            .toString();
            socket.sendText(json, true);
        }

        boolean awaitPayload(long timeout, TimeUnit unit) throws InterruptedException {
            return received.await(timeout, unit);
        }

        List<String> receivedContents() {
            return receivedContents;
        }

        void close() {
            if (socket != null) {
                socket.abort();
            }
        }

        private final class PeerListener implements WebSocket.Listener {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                webSocket.request(1);
                if (!last) {
                    return null;
                }
                String frame = buffer.toString();
                buffer.setLength(0);
                JsonNode envelope = objectMapper.readTree(frame);
                if ("message".equals(envelope.get("type").asString())) {
                    receivedContents.add(envelope.get("payload").get("content").asString());
                    received.countDown();
                }
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String wsBaseUrl() {
        return "ws://localhost:" + port;
    }
}
