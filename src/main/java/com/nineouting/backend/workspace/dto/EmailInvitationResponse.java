package com.nineouting.backend.workspace.dto;

import java.time.LocalDateTime;

/** REQ-127. 초대 메일을 보낼 이메일과 링크 만료 시각 (토큰은 메일로만 간다) */
public record EmailInvitationResponse(Long invitationId, String email, LocalDateTime expiresAt) {
}
