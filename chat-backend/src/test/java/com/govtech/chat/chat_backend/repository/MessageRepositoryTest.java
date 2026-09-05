package com.govtech.chat.chat_backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.govtech.chat.chat_backend.entity.Message;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * @DataJpaTest defaults to an in-memory H2 (not the file-mode DB used at runtime) — fine here
 * since these tests are about query correctness, not the persistence mode.
 */
@DataJpaTest
class MessageRepositoryTest {

    @Autowired private MessageRepository messageRepository;

    @Test
    void findConversation_returnsBothDirectionsOrderedById() {
        // Ids 1L/2L don't need real User rows to exist: fromId/toId are plain Long columns, not
        // JPA relationships, precisely so the write-path stays a single fast insert with no join.
        save(1L, 2L, "hi");
        save(2L, 1L, "hey back");
        save(1L, 2L, "how are you");
        save(1L, 3L, "unrelated conversation with a third user");

        List<Message> conversation = messageRepository.findConversation(1L, 2L);

        assertThat(conversation).extracting(Message::getContent)
                .containsExactly("hi", "hey back", "how are you");
    }

    @Test
    void findAllForUserAfter_onlyReturnsMessagesPastTheCursorId_forEitherDirection() {
        Message m1 = save(1L, 2L, "first");
        save(2L, 1L, "second");
        Message m3 = save(1L, 2L, "third");
        save(5L, 6L, "someone else's conversation entirely");

        List<Message> after = messageRepository.findAllForUserAfter(1L, m1.getId());

        // Excludes m1 itself (strictly greater than the cursor, not >=, so the client's
        // last-seen message isn't redelivered), includes m2 (the other direction) and m3, and
        // never includes the unrelated 5L/6L conversation.
        assertThat(after).extracting(Message::getContent).containsExactly("second", "third");
        assertThat(after).extracting(Message::getId).doesNotContain(m1.getId());
        assertThat(m3.getId()).isGreaterThan(m1.getId());
    }

    private Message save(long fromId, long toId, String content) {
        return messageRepository.save(new Message(fromId, toId, content, Instant.now()));
    }
}
