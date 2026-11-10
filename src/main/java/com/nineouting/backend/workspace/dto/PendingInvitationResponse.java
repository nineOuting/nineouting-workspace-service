package com.nineouting.backend.workspace.dto;

import java.time.LocalDateTime;

/**
 * REQ-164 이메일로 초대했지만 아직 수락하지 않은 사람 ("초대 대기").
 *
 * @param expired 유효 기간이 지나 메일 속 링크를 더는 쓸 수 없다 ([다시 보내기]로 새로 보낸다)
 */
public record PendingInvitationResponse(Long invitationId, String email, LocalDateTime invitedAt,
                                        LocalDateTime expiresAt, boolean expired) {
}
