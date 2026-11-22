package com.nineouting.backend.workspace.dto;

/**
 * 로그인 전 초대 첫 화면에 보여 줄 정보 (피그마 프론트 InviteLandingPage). 토큰만 알면 볼 수 있어 이름 두 개만 준다.
 *
 * @param inviterName 초대한 사람 이름. 찾지 못하면 null
 */
public record InvitePreviewResponse(String workspaceName, String inviterName) {
}
