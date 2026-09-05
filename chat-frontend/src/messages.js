// Message ids are the server's DB auto-increment (see backend Message entity) -- globally
// monotonic across all conversations, not just one thread. That's what makes both of these safe:
// dedup by id (an SSE push and a catch-up fetch can legitimately return the same message) and
// "highest id seen" as the catch-up cursor (no clock-skew risk from using a timestamp instead).

export function conversationKey(selfId, message) {
  return message.fromId === selfId ? message.toId : message.fromId;
}

// Merges new messages into an existing (assumed sorted, deduped) list for one conversation.
// Used both for the initial full-history load and for individual SSE pushes, so a thread's
// message list never depends on which of those two paths a given message arrived through.
export function mergeMessages(existing, incoming) {
  const seen = new Set(existing.map((m) => m.id));
  const additions = incoming.filter((m) => !seen.has(m.id));
  if (additions.length === 0) return existing;
  return [...existing, ...additions].sort((a, b) => a.id - b.id);
}

export function highestId(messages, current) {
  return messages.reduce((max, m) => Math.max(max, m.id), current);
}
