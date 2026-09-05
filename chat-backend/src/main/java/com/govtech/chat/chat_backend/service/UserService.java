package com.govtech.chat.chat_backend.service;

import com.govtech.chat.chat_backend.dto.UserResponse;
import com.govtech.chat.chat_backend.entity.User;
import com.govtech.chat.chat_backend.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse loginOrRegister(String username) {
        User user = userRepository
                        .findByUsername(username)
                        .orElseGet(() -> userRepository.save(new User(username)));
        return UserResponse.from(user);
    }

    public UserResponse requireUser(Long userId) {
        return UserResponse.from(userRepository.findById(userId).orElseThrow());
    }

    public List<UserResponse> listOtherUsers(Long selfId) {
        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(selfId))
                .map(UserResponse::from)
                .toList();
    }
}
