package com.nineouting.backend.workspace.dto;

/** 내부용: 사용자가 폴더에 들어갈 수 있는지 */
public record FolderAccessResponse(Long folderId, Long workspaceId, boolean allowed) {
}
