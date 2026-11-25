package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.InternalDefaultTemplateRequest;
import com.nineouting.backend.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출용 워크스페이스 주소. Gateway가 외부로 열지 않는다. */
@RestController
@RequiredArgsConstructor
public class InternalWorkspaceController {

    private final WorkspaceService workspaceService;

    /**
     * REQ-213 워크스페이스 양식함: minutes-service가 고른 양식을 워크스페이스 사본으로 만든 뒤 기본 양식으로 정한다.
     * 소유자 확인은 화면에서 부르는 기본 양식 바꾸기와 같다 (소유자가 아니면 403)
     */
    @PutMapping("/internal/workspaces/{workspaceId}/default-template")
    public ApiResponse<Void> changeDefaultTemplate(@PathVariable Long workspaceId,
                                                   @Valid @RequestBody InternalDefaultTemplateRequest request) {
        workspaceService.changeDefaultTemplate(workspaceId, request.userId(), request.templateId());
        return ApiResponse.ok(null);
    }
}
