import { useState } from "react";

export function LoginScreen({ onLogin }) {
  const [username, setUsername] = useState("");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    const trimmed = username.trim();
    if (!trimmed) return;
    setSubmitting(true);
    setError(null);
    try {
      await onLogin(trimmed);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>Chat</h1>
        <p className="login-hint">
          Pick any username. No password: two browser windows can log in as different users to
          message each other.
        </p>
        <input
          autoFocus
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Username"
          maxLength={64}
        />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting || !username.trim()}>
          {submitting ? "Joining..." : "Join"}
        </button>
      </form>
    </div>
  );
}
