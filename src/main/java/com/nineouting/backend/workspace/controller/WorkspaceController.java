package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.CreateWorkspaceRequest;
import com.nineouting.backend.workspace.dto.EmailInvitationResponse;
import com.nineouting.backend.workspace.dto.EmailInviteRequest;
import com.nineouting.backend.workspace.dto.FolderAccessRequest;
import com.nineouting.backend.workspace.dto.InviteLinkResponse;
import com.nineouting.backend.workspace.dto.JoinRequestResponse;
import com.nineouting.backend.workspace.dto.MemberResponse;
import com.nineouting.backend.workspace.dto.UserIdRequest;
import com.nineouting.backend.workspace.dto.WorkspaceDetailResponse;
import com.nineouting.backend.workspace.dto.WorkspaceResponse;
import com.nineouting.backend.workspace.service.EmailInvitationService;
import com.nineouting.backend.workspace.service.MembershipService;
import com.nineouting.backend.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final MembershipService membershipService;
    private final EmailInvitationService emailInvitationService;

    /** REQ-031 */
    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(@CurrentUser AuthenticatedUser user,
                                                                 @Valid @RequestBody CreateWorkspaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(workspaceService.create(user.userId(), request.name())));
    }

    /** REQ-029~030 */
    @GetMapping
    public ApiResponse<List<WorkspaceResponse>> findMine(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(workspaceService.findMine(user.userId()));
    }

    /** REQ-028 */
    /** REQ-138. 소유자만. 안의 폴더·회의·회의록도 함께 지운다 */
    @DeleteMapping("/{workspaceId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        workspaceService.delete(workspaceId, user.userId());
        return ApiResponse.ok();
    }

    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceDetailResponse> get(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        return ApiResponse.ok(workspaceService.get(workspaceId, user.userId()));
    }

    /** REQ-030 */
    @PutMapping("/{workspaceId}/last-visited")
    public ApiResponse<Void> markLastVisited(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        workspaceService.markLastVisited(workspaceId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-034 */
    /** REQ-127. 소유자만. 이메일로 초대 메일을 보낸다 (받은 사람은 승인 없이 바로 들어온다) */
    @PostMapping("/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<EmailInvitationResponse>> inviteByEmail(@CurrentUser AuthenticatedUser user,
                                                                              @PathVariable Long workspaceId,
                                                                              @Valid @RequestBody EmailInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(emailInvitationService.invite(workspaceId, user.userId(), request.email())));
    }

    @PostMapping("/{workspaceId}/invite-link")
    public ApiResponse<InviteLinkResponse> issueInviteLink(@CurrentUser AuthenticatedUser user,
                                                           @PathVariable Long workspaceId) {
        return ApiResponse.ok(membershipService.issueInviteLink(workspaceId, user.userId()));
    }

    /** REQ-035 (레거시) */
    @GetMapping("/{workspaceId}/join-requests")
    public ApiResponse<List<JoinRequestResponse>> joinRequests(@CurrentUser AuthenticatedUser user,
                                                               @PathVariable Long workspaceId) {
        return ApiResponse.ok(membershipService.pendingJoinRequests(workspaceId, user.userId()));
    }

    @PostMapping("/{workspaceId}/join-requests/{joinRequestId}/approve")
    public ApiResponse<Void> approve(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                     @PathVariable Long joinRequestId) {
        membershipService.approve(workspaceId, joinRequestId, user.userId());
        return ApiResponse.ok();
    }

    @PostMapping("/{workspaceId}/join-requests/{joinRequestId}/reject")
    public ApiResponse<Void> reject(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                    @PathVariable Long joinRequestId) {
        membershipService.reject(workspaceId, joinRequestId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-033 */
    @GetMapping("/{workspaceId}/members")
    public ApiResponse<List<MemberResponse>> members(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        return ApiResponse.ok(membershipService.members(workspaceId, user.userId()));
    }

    /** REQ-032. 경로가 더 구체적이라 아래 {userId}보다 먼저 맞는다. */
    @DeleteMapping("/{workspaceId}/members/me")
    public ApiResponse<Void> leave(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        membershipService.leave(workspaceId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-128. 소유자만. 멤버의 폴더 접근 권한을 ALL(모든 폴더) / INVITED(초대된 폴더만)로 바꾼다 */
    @PutMapping("/{workspaceId}/members/{userId}/folder-access")
    public ApiResponse<Void> changeFolderAccess(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                                @PathVariable Long userId, @Valid @RequestBody FolderAccessRequest request) {
        membershipService.changeFolderAccess(workspaceId, user.userId(), userId, request.folderAccess());
        return ApiResponse.ok();
    }

    /** REQ-036 내보내기 */
    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ApiResponse<Void> removeMember(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                          @PathVariable Long userId) {
        membershipService.removeMember(workspaceId, user.userId(), userId);
        return ApiResponse.ok();
    }

    /** REQ-129 워크스페이스 소유권 양도. 소유자만, 다른 멤버에게. 소유자는 1명이다 */
    @PutMapping("/{workspaceId}/owner")
    public ApiResponse<Void> transferOwnership(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                               @Valid @RequestBody UserIdRequest request) {
        membershipService.transferOwnership(workspaceId, user.userId(), request.userId());
        return ApiResponse.ok();
    }
}
