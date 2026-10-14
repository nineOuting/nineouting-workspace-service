package com.nineouting.backend.workspace.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param frontendBaseUrl 초대 링크 주소를 만들 때 쓰는 프론트 주소
 * @param inviteLinkTtl   초대 링크 유효 기간 (설계: 1일)
 * @param userServiceUrl  사용자 이름·이메일 조회 (/internal/users)
 */
@ConfigurationProperties(prefix = "nineouting.workspace")
public record WorkspaceProperties(String frontendBaseUrl, Duration inviteLinkTtl, String userServiceUrl) {
}
