package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFolderRequest(
        @NotBlank(message = "폴더명을 입력해 주세요")
        @Size(max = 50, message = "폴더명은 50자 이하로 입력해 주세요")
        String name
) {
    public RenameFolderRequest {
        name = name == null ? null : name.trim();
    }
}
