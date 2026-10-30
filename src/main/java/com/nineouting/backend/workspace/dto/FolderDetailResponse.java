package com.nineouting.backend.workspace.dto;

import java.util.List;

/**
 * @param path 화면 위 경로 표시용 (REQ-002). 들어갈 수 없는 상위 폴더는 accessible=false
 */
public record FolderDetailResponse(Long folderId, Long workspaceId, Long parentId, String name, boolean isDefault,
                                   Long ownerUserId, boolean isOwner, List<PathItem> path) {

    public record PathItem(Long folderId, String name, boolean accessible) {
    }
}
