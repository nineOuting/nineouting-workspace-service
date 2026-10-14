package com.nineouting.backend.workspace.dto;

/** 내부용: 사용자가 들어갈 수 있는 폴더 */
public record AccessibleFolderResponse(Long folderId, Long workspaceId, Long parentId, String name) {
}
