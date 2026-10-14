package com.nineouting.backend.workspace.dto;

import com.nineouting.backend.workspace.domain.WorkspaceRole;

public record WorkspaceDetailResponse(Long workspaceId, String name, WorkspaceRole myRole, long memberCount) {
}
