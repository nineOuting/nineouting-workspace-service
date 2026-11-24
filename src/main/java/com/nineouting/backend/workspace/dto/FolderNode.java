package com.nineouting.backend.workspace.dto;

import java.util.List;

/**
 * 폴더 목록의 한 항목. 들어갈 수 없는 폴더(초대받지 않은 프로젝트)도 이름만 보이고 accessible=false다.
 */
public record FolderNode(Long folderId, String name, boolean isDefault, Long ownerUserId, boolean accessible,
                         List<FolderNode> children, int color) {
}
