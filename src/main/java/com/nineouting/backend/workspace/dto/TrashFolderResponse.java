package com.nineouting.backend.workspace.dto;

import java.time.LocalDateTime;

/**
 * 쓰레기통의 폴더 (REQ-143).
 *
 * @param deletedByName 지운 사람 이름. 탈퇴 등으로 찾을 수 없으면 null
 * @param purgeAt       이 시각이 지나면 자동으로 영구 삭제된다
 */
public record TrashFolderResponse(Long folderId, String name, LocalDateTime deletedAt, Long deletedByUserId,
                                  String deletedByName, LocalDateTime purgeAt) {
}
