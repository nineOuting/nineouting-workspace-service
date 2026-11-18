package com.nineouting.backend.workspace.dto;

/**
 * 내부용: 사용자가 폴더에 들어갈 수 있는지.
 *
 * @param owner 폴더 소유자인지. 소유자가 없는 기본 폴더는 워크스페이스 소유자 (REQ-160 회의록 삭제)
 */
public record FolderAccessResponse(Long folderId, Long workspaceId, boolean allowed, boolean owner) {
}
