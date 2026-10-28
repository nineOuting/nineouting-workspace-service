package com.nineouting.backend.workspace.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param frontendBaseUrl 초대 링크 주소를 만들 때 쓰는 프론트 주소
 * @param inviteLinkTtl   초대 링크 유효 기간 (설계: 1일)
 * @param trashRetention  쓰레기통 보관 기간 (REQ-143: 3개월, 기본 90일). 지나면 자동으로 영구 삭제
 */
@ConfigurationProperties(prefix = "nineouting.workspace")
public record WorkspaceProperties(String frontendBaseUrl, Duration inviteLinkTtl, Duration trashRetention) {

    public WorkspaceProperties {
        trashRetention = trashRetention == null ? Duration.ofDays(90) : trashRetention;
    }
}
