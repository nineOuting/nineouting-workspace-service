package com.nineouting.backend.workspace.dto;

/**
 * REQ-127. 초대 링크를 연 사람에게 보여 줄 정보.
 *
 * @param emailMatches 로그인한 계정의 이메일이 초대받은 이메일과 같은지 (다르면 수락할 수 없다)
 * @param member       이미 워크스페이스 멤버인지
 */
public record EmailInvitationInfoResponse(Long workspaceId, String workspaceName, String email, boolean emailMatches,
                                          boolean member) {
}
