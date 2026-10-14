package com.nineouting.backend.workspace.dto;

/** 내부용: 사용자가 들어갈 수 있는 폴더. 워크스페이스 이름은 홈 캘린더 표시용 (REQ-019) */
public record AccessibleFolderResponse(Long folderId, Long workspaceId, String workspaceName, Long parentId,
                                       String name) {
}
