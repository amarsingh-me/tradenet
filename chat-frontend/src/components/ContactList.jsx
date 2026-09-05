export function ContactList({ users, activeUserId, onSelect, currentUsername }) {
  return (
    <aside className="contact-list">
      <div className="contact-list-header">
        <strong>{currentUsername}</strong>
        <span className="muted">logged in</span>
      </div>
      {users.length === 0 && <p className="muted contact-empty">No other users yet</p>}
      <ul>
        {users.map((u) => (
          <li key={u.id}>
            <button
              className={u.id === activeUserId ? "contact active" : "contact"}
              onClick={() => onSelect(u.id)}
            >
              {u.username}
            </button>
          </li>
        ))}
      </ul>
    </aside>
  );
}
