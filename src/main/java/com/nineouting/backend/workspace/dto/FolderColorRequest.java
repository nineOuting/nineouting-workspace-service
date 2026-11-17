package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** REQ-202 프로젝트 색. 8가지 색 중 하나 (0~7, 화면이 색을 정한다) */
public record FolderColorRequest(
        @NotNull(message = "색을 골라 주세요")
        @Min(value = 0, message = "색을 다시 골라 주세요")
        @Max(value = 7, message = "색을 다시 골라 주세요")
        Integer color
) {
}
