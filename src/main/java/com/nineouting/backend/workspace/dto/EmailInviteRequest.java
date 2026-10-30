package com.nineouting.backend.workspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** REQ-127 */
public record EmailInviteRequest(
        @NotBlank(message = "이메일을 입력해 주세요")
        @Email(message = "올바른 이메일 형식을 입력해 주세요")
        @Size(max = 255, message = "이메일이 너무 깁니다")
        String email
) {
}
