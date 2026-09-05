package com.govtech.chat.chat_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotNull Long toId, @NotBlank @Size(max = 4000) String content) {}
