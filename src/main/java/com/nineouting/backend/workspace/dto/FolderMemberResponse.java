package com.nineouting.backend.workspace.dto;

/**
 * @param owner     폴더를 만든 사람(소유자)
 * @param inherited 상위 폴더 멤버라서 들어갈 수 있는 사람 (이 폴더에서 내보낼 수 없다)
 */
public record FolderMemberResponse(Long userId, String name, String email, String profileImageUrl, boolean owner,
                                   boolean inherited) {
}
