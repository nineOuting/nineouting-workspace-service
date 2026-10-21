package com.nineouting.backend.workspace.dto;

import com.nineouting.backend.workspace.domain.WorkspaceRole;
import java.time.LocalDateTime;

/** REQ-033: 이름, 이메일, 권한 */
public record MemberResponse(Long userId, String name, String email, String profileImageUrl, WorkspaceRole role,
                             LocalDateTime joinedAt) {
}
