package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.CreateFolderRequest;
import com.nineouting.backend.workspace.dto.FolderDetailResponse;
import com.nineouting.backend.workspace.dto.FolderMemberResponse;
import com.nineouting.backend.workspace.dto.FolderNode;
import com.nineouting.backend.workspace.dto.RecentFolderResponse;
import com.nineouting.backend.workspace.dto.RenameFolderRequest;
import com.nineouting.backend.workspace.dto.UserIdRequest;
import com.nineouting.backend.workspace.service.FolderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    /** REQ-037 */
    @GetMapping("/api/workspaces/{workspaceId}/folders")
    public ApiResponse<List<FolderNode>> list(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        return ApiResponse.ok(folderService.list(workspaceId, user.userId()));
    }

    /** REQ-039~040, 046~047 */
    @PostMapping("/api/workspaces/{workspaceId}/folders")
    public ResponseEntity<ApiResponse<FolderNode>> create(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                                          @Valid @RequestBody CreateFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(folderService.create(workspaceId, user.userId(), request.name(), request.parentId())));
    }

    /** REQ-038 */
    @GetMapping("/api/folders/{folderId}")
    public ApiResponse<FolderDetailResponse> open(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId) {
        return ApiResponse.ok(folderService.open(folderId, user.userId()));
    }

    @PatchMapping("/api/folders/{folderId}")
    public ApiResponse<FolderNode> rename(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId,
                                          @Valid @RequestBody RenameFolderRequest request) {
        return ApiResponse.ok(folderService.rename(folderId, user.userId(), request.name()));
    }

    /** REQ-058 */
    @DeleteMapping("/api/folders/{folderId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId) {
        folderService.delete(folderId, user.userId());
        return ApiResponse.ok();
    }

    /** REQ-041 */
    @GetMapping("/api/folders/{folderId}/members")
    public ApiResponse<List<FolderMemberResponse>> members(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId) {
        return ApiResponse.ok(folderService.members(folderId, user.userId()));
    }

    @PostMapping("/api/folders/{folderId}/members")
    public ApiResponse<Void> invite(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId,
                                    @Valid @RequestBody UserIdRequest request) {
        folderService.invite(folderId, user.userId(), request.userId());
        return ApiResponse.ok();
    }

    /** REQ-042 */
    @DeleteMapping("/api/folders/{folderId}/members/{userId}")
    public ApiResponse<Void> removeMember(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId,
                                          @PathVariable Long userId) {
        folderService.removeMember(folderId, user.userId(), userId);
        return ApiResponse.ok();
    }

    /** REQ-043 */
    @PutMapping("/api/folders/{folderId}/owner")
    public ApiResponse<Void> transferOwner(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId,
                                           @Valid @RequestBody UserIdRequest request) {
        folderService.transferOwner(folderId, user.userId(), request.userId());
        return ApiResponse.ok();
    }

    /** REQ-024~025 */
    @GetMapping("/api/users/me/recent-folders")
    public ApiResponse<List<RecentFolderResponse>> recent(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(folderService.recent(user.userId()));
    }
}
