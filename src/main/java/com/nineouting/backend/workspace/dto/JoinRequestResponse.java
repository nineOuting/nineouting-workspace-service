package com.nineouting.backend.workspace.dto;

import com.nineouting.backend.workspace.domain.JoinRequestStatus;
import java.time.LocalDateTime;

public record JoinRequestResponse(Long joinRequestId, Long userId, String name, String email,
                                  JoinRequestStatus status, LocalDateTime requestedAt) {
}
