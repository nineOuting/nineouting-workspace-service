package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.TrashFolderResponse;
import com.nineouting.backend.workspace.service.TrashService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 쓰레기통 (REQ-143). 지금은 폴더만. 지운 사람과 워크스페이스 소유자가 보고 복구·영구 삭제한다 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/trash")
public class TrashController {

    private final TrashService trashService;

    @GetMapping
    public ApiResponse<List<TrashFolderResponse>> list(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId) {
        return ApiResponse.ok(trashService.list(workspaceId, user.userId()));
    }

    @PostMapping("/folders/{folderId}/restore")
    public ApiResponse<Void> restore(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                     @PathVariable Long folderId) {
        trashService.restore(workspaceId, folderId, user.userId());
        return ApiResponse.ok();
    }

    /** 영구 삭제. 안의 회의·회의록도 지운다 */
    @DeleteMapping("/folders/{folderId}")
    public ApiResponse<Void> purge(@CurrentUser AuthenticatedUser user, @PathVariable Long workspaceId,
                                   @PathVariable Long folderId) {
        trashService.purge(workspaceId, folderId, user.userId());
        return ApiResponse.ok();
    }
}
