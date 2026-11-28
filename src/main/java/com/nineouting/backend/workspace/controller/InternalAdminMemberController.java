package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.InternalAdminMemberRequest;
import com.nineouting.backend.workspace.dto.InternalAdminMemberResponse;
import com.nineouting.backend.workspace.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** REQ-218 관리자 빠른 작업 (admin-service만 부른다). Gateway가 외부로 열지 않는다. */
@RestController
@RequiredArgsConstructor
public class InternalAdminMemberController {

    private final MembershipService membershipService;

    /** 사용자를 일반 멤버로 넣는다. 이미 멤버면 그대로 둔다 */
    @PostMapping("/internal/admin/workspaces/{workspaceId}/members")
    public ApiResponse<InternalAdminMemberResponse> add(@PathVariable Long workspaceId,
                                                        @Valid @RequestBody InternalAdminMemberRequest request) {
        boolean added = membershipService.addByAdmin(workspaceId, request.userId());
        return ApiResponse.ok(new InternalAdminMemberResponse(workspaceId, request.userId(), added));
    }
}
