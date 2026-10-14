package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotNull;

public record UserIdRequest(@NotNull(message = "userId를 보내 주세요") Long userId) {
}
