package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.domain.FavoriteType;
import com.nineouting.backend.workspace.dto.FavoriteResponse;
import com.nineouting.backend.workspace.service.FavoriteService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** 워크스페이스·폴더 즐겨찾기 (REQ-143~144) */
@RestController
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** REQ-143 */
    @GetMapping("/api/users/me/favorites")
    public ApiResponse<List<FavoriteResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(favoriteService.list(user.userId()));
    }

    /** REQ-144 */
    @PutMapping("/api/workspaces/{workspaceId}/favorite")
    public ApiResponse<Void> addWorkspace(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        favoriteService.addWorkspace(workspaceId, user.userId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/workspaces/{workspaceId}/favorite")
    public ApiResponse<Void> removeWorkspace(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        favoriteService.remove(FavoriteType.WORKSPACE, workspaceId, user.userId());
        return ApiResponse.ok();
    }

    @PutMapping("/api/folders/{folderId}/favorite")
    public ApiResponse<Void> addFolder(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId) {
        favoriteService.addFolder(folderId, user.userId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/folders/{folderId}/favorite")
    public ApiResponse<Void> removeFolder(@CurrentUser AuthenticatedUser user, @PathVariable Long folderId) {
        favoriteService.remove(FavoriteType.FOLDER, folderId, user.userId());
        return ApiResponse.ok();
    }
}
