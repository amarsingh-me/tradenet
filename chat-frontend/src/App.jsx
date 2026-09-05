import { useEffect, useRef, useState } from "react";
import { api } from "./api";
import { connectChatSocket } from "./ws";
import { conversationKey, highestId, mergeMessages } from "./messages";
import { LoginScreen } from "./components/LoginScreen";
import { ContactList } from "./components/ContactList";
import { ChatThread } from "./components/ChatThread";
import "./App.css";

function App() {
  const [user, setUser] = useState(null);
  const [users, setUsers] = useState([]);
  const [activeUserId, setActiveUserId] = useState(null);
  const [messagesByUser, setMessagesByUser] = useState({});

  // Refs, not state: these track internal bookkeeping that event handlers need the latest value
  // of but that should never itself trigger a re-render.
  const lastSeenId = useRef(0);
  const loadedConversations = useRef(new Set());
  const hasConnectedOnce = useRef(false);
  const socketRef = useRef(null);

  async function handleLogin(username) {
    const loggedInUser = await api.login(username);
    setUser(loggedInUser);
    setUsers(await api.listUsers());
  }

  function applyIncoming(selfId, incomingMessages) {
    if (!incomingMessages || incomingMessages.length === 0) return;
    setMessagesByUser((prev) => {
      const next = { ...prev };
      for (const msg of incomingMessages) {
        const key = conversationKey(selfId, msg);
        next[key] = mergeMessages(next[key] || [], [msg]);
      }
      return next;
    });
    lastSeenId.current = highestId(incomingMessages, lastSeenId.current);
  }

  // One WebSocket connection for the whole app (not per-thread), opened once on login and kept
  // open regardless of which contact is currently selected -- otherwise switching threads would
  // drop messages pushed while a different thread was open.
  useEffect(() => {
    if (!user) return undefined;

    const socket = connectChatSocket({
      onMessage: (payload) => applyIncoming(user.id, [payload]),
      // Fires on the initial connect and after every reconnect (native WebSocket has no built-in
      // auto-reconnect, so ws.js runs its own backoff loop -- see there). Only the reconnect case
      // needs a catch-up fetch -- the initial connect has nothing to catch up on yet.
      onOpen: () => {
        if (hasConnectedOnce.current) {
          api.messagesAfter(lastSeenId.current).then((missed) => applyIncoming(user.id, missed));
        }
        hasConnectedOnce.current = true;
      },
    });
    socketRef.current = socket;

    return () => socket.close();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  async function selectContact(otherId) {
    setActiveUserId(otherId);
    if (loadedConversations.current.has(otherId)) return;
    loadedConversations.current.add(otherId);
    const history = await api.conversation(otherId);
    setMessagesByUser((prev) => ({
      ...prev,
      [otherId]: mergeMessages(prev[otherId] || [], history),
    }));
    lastSeenId.current = highestId(history, lastSeenId.current);
  }

  function sendMessage(toId, content) {
    socketRef.current?.send({ toId, content });
  }

  if (!user) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  const activeUser = users.find((u) => u.id === activeUserId);

  return (
    <div className="app">
      <ContactList
        users={users}
        activeUserId={activeUserId}
        onSelect={selectContact}
        currentUsername={user.username}
      />
      {activeUser ? (
        <ChatThread
          selfId={user.id}
          otherUser={activeUser}
          messages={messagesByUser[activeUserId] || []}
          onSend={sendMessage}
        />
      ) : (
        <div className="chat-thread empty-state">
          <p className="muted">Select a conversation to start chatting</p>
        </div>
      )}
    </div>
  );
}

export default App;
