import { useEffect, useRef, useState } from "react";

export function ChatThread({ selfId, otherUser, messages, onSend }) {
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  async function handleSubmit(e) {
    e.preventDefault();
    const content = draft.trim();
    if (!content) return;
    setSending(true);
    try {
      // No optimistic append here: the sent message shows up once it comes back over SSE (the
      // server echoes every dispatched message to the sender's own connection too), so the
      // thread only ever renders what the server actually persisted -- see MessageDispatchService.
      await onSend(otherUser.id, content);
      setDraft("");
    } finally {
      setSending(false);
    }
  }

  return (
    <section className="chat-thread">
      <header className="chat-thread-header">{otherUser.username}</header>
      <div className="message-list">
        {messages.map((m) => (
          <div key={m.id} className={m.fromId === selfId ? "message mine" : "message theirs"}>
            <span className="message-content">{m.content}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <form className="composer" onSubmit={handleSubmit}>
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={`Message ${otherUser.username}`}
          maxLength={4000}
          autoFocus
        />
        <button type="submit" disabled={sending || !draft.trim()}>
          Send
        </button>
      </form>
    </section>
  );
}
