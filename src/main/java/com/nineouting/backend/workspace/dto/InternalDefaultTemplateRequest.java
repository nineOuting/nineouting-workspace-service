package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.NotNull;

/** REQ-213 minutes-service가 보내는 기본 양식 지정 요청. userId는 지정을 누른 사람 (소유자인지 여기서 확인한다) */
public record InternalDefaultTemplateRequest(@NotNull Long userId, @NotNull Long templateId) {
}
