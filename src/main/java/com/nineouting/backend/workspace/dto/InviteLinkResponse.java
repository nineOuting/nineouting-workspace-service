package com.nineouting.backend.workspace.dto;

import java.time.LocalDateTime;

/**
 * @param inviteUrl 프론트의 초대 화면 주소. 복사해서 공유한다 (REQ-033)
 * @param expiresAt UTC
 */
public record InviteLinkResponse(String token, String inviteUrl, LocalDateTime expiresAt) {
}
