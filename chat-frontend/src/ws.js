const INITIAL_BACKOFF_MS = 500;
const MAX_BACKOFF_MS = 15000;

// Native WebSocket, unlike EventSource, does not auto-reconnect on drop -- without this, losing
// one connection would silently kill real-time delivery for the rest of the session. Backoff
// (capped, with jitter) avoids hammering the server on a real outage; `manuallyClosed` stops the
// loop on logout/unmount instead of reconnecting forever after a deliberate close.
export function connectChatSocket({ onMessage, onOpen }) {
  let socket = null;
  let manuallyClosed = false;
  let backoffMs = INITIAL_BACKOFF_MS;
  let reconnectTimer = null;

  function wsUrl() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/ws/chat`;
  }

  function open() {
    socket = new WebSocket(wsUrl());

    socket.onopen = () => {
      backoffMs = INITIAL_BACKOFF_MS;
      onOpen();
    };

    socket.onmessage = (event) => {
      const frame = JSON.parse(event.data);
      if (frame.type === "message") {
        onMessage(frame.payload);
      } else if (frame.type === "error") {
        console.warn("Server rejected a message:", frame.payload?.reason);
      }
    };

    socket.onclose = scheduleReconnect;
    socket.onerror = () => socket.close();
  }

  function scheduleReconnect() {
    if (manuallyClosed) return;
    reconnectTimer = setTimeout(() => {
      backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
      open();
    }, backoffMs + Math.random() * 250);
  }

  open();

  return {
    send(payload) {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(payload));
      }
    },
    close() {
      manuallyClosed = true;
      clearTimeout(reconnectTimer);
      socket?.close();
    },
  };
}
