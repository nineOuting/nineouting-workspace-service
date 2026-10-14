package com.nineouting.backend.workspace.dto;

import java.util.List;

/**
 * 폴더 목록의 한 항목. 하위 폴더에만 초대된 경우 상위 폴더는 이름만 보이고 accessible=false다.
 */
public record FolderNode(Long folderId, String name, boolean isDefault, Long ownerUserId, boolean accessible,
                         List<FolderNode> children) {
}
