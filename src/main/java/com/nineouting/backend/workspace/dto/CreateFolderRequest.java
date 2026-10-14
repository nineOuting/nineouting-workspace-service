package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param parentId 하위 폴더를 만들 때 상위 폴더 ID. 프로젝트 폴더면 null
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
