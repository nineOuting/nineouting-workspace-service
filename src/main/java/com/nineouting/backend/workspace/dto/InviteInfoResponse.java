package com.nineouting.backend.workspace.dto;

/**
 * 초대 링크로 들어온 사용자에게 보여 줄 정보 (REQ-015).
 *
 * @param inviterName 링크를 마지막으로 발급한 소유자 이름 (MSG-059). 찾지 못하면 null
 * @param myStatus MEMBER(이미 멤버) / PENDING(승인 대기 중) / REJECTED(거절됨, 다시 요청 가능) / NONE(요청 가능)
 */
public record InviteInfoResponse(Long workspaceId, String workspaceName, String inviterName, String myStatus) {
}
