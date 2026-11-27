package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotNull;

/** REQ-218 관리자가 워크스페이스에 넣을 사용자 */
public record InternalAdminMemberRequest(@NotNull(message = "사용자를 골라 주세요") Long userId) {
}
