package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.InviteInfoResponse;
import com.nineouting.backend.workspace.dto.JoinRequestResponse;
import com.nineouting.backend.workspace.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 초대 링크로 들어온 사용자 (REQ-015~017). 로그인은 필요하다. */
@RestController
@RequestMapping("/api/invites/{token}")
@RequiredArgsConstructor
public class InviteController {

    private final MembershipService membershipService;

    @GetMapping
    public ApiResponse<InviteInfoResponse> info(@CurrentUser AuthenticatedUser user, @PathVariable String token) {
        return ApiResponse.ok(membershipService.inviteInfo(token, user.userId()));
    }

    @PostMapping("/join-requests")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> requestJoin(@CurrentUser AuthenticatedUser user,
                                                                        @PathVariable String token) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(membershipService.requestJoin(token, user.userId())));
    }
}
