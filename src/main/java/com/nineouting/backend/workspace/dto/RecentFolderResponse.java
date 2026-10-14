package com.nineouting.backend.workspace.dto;

import java.time.LocalDateTime;

/** 홈 화면의 최근 프로젝트 (REQ-024) */
public record RecentFolderResponse(Long folderId, String folderName, Long workspaceId, String workspaceName,
                                   LocalDateTime visitedAt) {
}
