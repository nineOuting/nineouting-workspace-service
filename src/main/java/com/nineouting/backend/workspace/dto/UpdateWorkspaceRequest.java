package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * REQ-163 워크스페이스 이름, 설명 수정.
 *
 * @param description 비우거나 보내지 않으면 빈 설명이 된다
 */
public record UpdateWorkspaceRequest(
        @NotBlank(message = "워크스페이스 이름을 입력해 주세요")
        @Size(max = 50, message = "워크스페이스 이름은 50자 이하로 입력해 주세요")
        String name,
        @Size(max = 100, message = "워크스페이스 설명은 100자 이하로 입력해 주세요")
        String description
) {
    public UpdateWorkspaceRequest {
        name = name == null ? null : name.trim();
        description = description == null ? "" : description.trim();
    }
}
