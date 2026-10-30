package com.nineouting.backend.workspace.dto;

import com.nineouting.backend.workspace.domain.FavoriteType;

/** 홈의 즐겨찾기 (REQ-151). 워크스페이스면 folderId·folderName이 null */
public record FavoriteResponse(FavoriteType type, Long workspaceId, String workspaceName, Long folderId,
                               String folderName) {
}
