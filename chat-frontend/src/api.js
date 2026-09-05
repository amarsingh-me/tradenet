const BASE = "/api";

// credentials: 'include' on every call so the session cookie set by /login rides along on the
// same-origin nginx-proxied requests (see nginx.conf) -- without it the browser will silently
// treat every subsequent call as logged out.
async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Request failed: ${res.status}`);
  }
  if (res.status === 202 || res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  login: (username) =>
    request("/login", { method: "POST", body: JSON.stringify({ username }) }),
  listUsers: () => request("/users"),
  conversation: (otherId) => request(`/messages/${otherId}`),
  messagesAfter: (lastId) => request(`/messages/after/${lastId}`),
};
