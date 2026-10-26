package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param parentId 보내면 FOLDER_DEPTH_EXCEEDED (폴더 안에 폴더는 만들 수 없다, REQ-040)
 */
public record CreateFolderRequest(
        @NotBlank(message = "폴더명을 입력해 주세요")
        @Size(max = 50, message = "폴더명은 50자 이하로 입력해 주세요")
        String name,
        Long parentId
) {
    public CreateFolderRequest {
        name = name == null ? null : name.trim();
    }
}
