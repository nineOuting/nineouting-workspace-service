package com.nineouting.backend.workspace.dto;

import com.nineouting.backend.workspace.domain.FavoriteType;

/** 홈의 즐겨찾기 (REQ-151). 워크스페이스면 folderId·folderName·folderColor가 null. folderColor는 프로젝트 색 (REQ-202) */
public record FavoriteResponse(FavoriteType type, Long workspaceId, String workspaceName, Long folderId,
                               String folderName, Integer folderColor) {
}
