package com.nineouting.backend.workspace.dto;

import com.nineouting.backend.workspace.domain.WorkspaceRole;

/**
 * @param lastVisited 마지막으로 접속한 워크스페이스인지 (REQ-030)
 */
public record WorkspaceResponse(Long workspaceId, String name, WorkspaceRole myRole, boolean lastVisited) {
}
