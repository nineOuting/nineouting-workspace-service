package com.nineouting.backend.workspace.dto;

/** REQ-218 added가 false면 이미 멤버여서 바꾸지 않았다 */
public record InternalAdminMemberResponse(Long workspaceId, Long userId, boolean added) {
}
