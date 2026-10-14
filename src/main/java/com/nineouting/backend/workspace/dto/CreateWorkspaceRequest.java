package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank(message = "워크스페이스 이름을 입력해 주세요")
        @Size(max = 50, message = "워크스페이스 이름은 50자 이하로 입력해 주세요")
        String name
) {
    public CreateWorkspaceRequest {
        name = name == null ? null : name.trim();
    }
}
