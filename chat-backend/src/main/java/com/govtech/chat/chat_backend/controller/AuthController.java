package com.govtech.chat.chat_backend.controller;

import com.govtech.chat.chat_backend.dto.LoginRequest;
import com.govtech.chat.chat_backend.dto.UserResponse;
import com.govtech.chat.chat_backend.service.UserService;
import com.govtech.chat.chat_backend.web.SessionUser;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Username-only "login": no password. This is a deliberate MVP scope cut, not an oversight —
    // anyone can claim any username. Acceptable for a local demo of the messaging core, called out
    // explicitly as a known gap rather than left implicit.
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        UserResponse user = userService.loginOrRegister(request.username().trim());
        SessionUser.login(session, user.id(), user.username());
        return user;
    }

    @GetMapping("/me")
    public UserResponse me(HttpSession session) {
        Long userId = SessionUser.requireUserId(session);
        return userService.requireUser(userId);
    }

    @GetMapping("/users")
    public List<UserResponse> listOtherUsers(HttpSession session) {
        Long selfId = SessionUser.requireUserId(session);
        return userService.listOtherUsers(selfId);
    }
}
