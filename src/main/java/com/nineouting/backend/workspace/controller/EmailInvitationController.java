package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.EmailInvitationInfoResponse;
import com.nineouting.backend.workspace.dto.InvitePreviewResponse;
import com.nineouting.backend.workspace.dto.WorkspaceResponse;
import com.nineouting.backend.workspace.service.EmailInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 초대 링크 (REQ-127). Gateway가 /api/workspaces/**를 넘기므로 그 아래에 둔다.
 * 로그인이 필요하다 (가입 전이면 프론트가 회원가입으로 보낸 뒤 다시 연다).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/invitations/{token}")
public class EmailInvitationController {

    private final EmailInvitationService invitationService;

    @GetMapping
    public ApiResponse<EmailInvitationInfoResponse> info(@CurrentUser AuthenticatedUser user, @PathVariable String token) {
        return ApiResponse.ok(invitationService.info(token, user.userId()));
    }

    /** 로그인 전 초대 첫 화면 (Gateway, 서비스 모두 인증 없이 연다) */
    @GetMapping("/preview")
    public ApiResponse<InvitePreviewResponse> preview(@PathVariable String token) {
        return ApiResponse.ok(invitationService.preview(token));
    }

    @PostMapping("/accept")
    public ApiResponse<WorkspaceResponse> accept(@CurrentUser AuthenticatedUser user, @PathVariable String token) {
        return ApiResponse.ok(invitationService.accept(token, user.userId()));
    }
}
