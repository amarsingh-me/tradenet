package com.govtech.chat.chat_backend.web;

import jakarta.servlet.http.HttpSession;

/**
 * Thin wrapper around the servlet container's default in-memory HttpSession — deliberately not
 * Spring Session / a shared store. That makes this app single-instance only (a second backend
 * replica wouldn't see another instance's sessions), which is an accepted limitation given the
 * assessment runs locally/single-container; a production deployment behind a load balancer would
 * need sticky sessions or a shared session store.
 */
public final class SessionUser {

    static final String USER_ID_KEY = "userId";
    static final String USERNAME_KEY = "username";

    private SessionUser() {}

    public static void login(HttpSession session, Long userId, String username) {
        session.setAttribute(USER_ID_KEY, userId);
        session.setAttribute(USERNAME_KEY, username);
    }

    public static Long requireUserId(HttpSession session) {
        Object userId = session.getAttribute(USER_ID_KEY);
        if (userId == null) {
            throw new UnauthenticatedException();
        }
        return (Long) userId;
    }

    public static String requireUsername(HttpSession session) {
        Object username = session.getAttribute(USERNAME_KEY);
        if (username == null) {
            throw new UnauthenticatedException();
        }
        return (String) username;
    }
}
