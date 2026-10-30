package com.nineouting.backend.workspace.controller;

import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.workspace.dto.AccessibleFolderResponse;
import com.nineouting.backend.workspace.dto.FolderAccessResponse;
import com.nineouting.backend.workspace.service.InternalFolderService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 조회용. Gateway가 외부로 열지 않는다. */
@RestController
@RequiredArgsConstructor
public class InternalFolderController {

    private final InternalFolderService internalFolderService;

    @GetMapping("/internal/folders/{folderId}/access")
    public ApiResponse<FolderAccessResponse> access(@PathVariable Long folderId, @RequestParam Long userId) {
        return ApiResponse.ok(internalFolderService.access(folderId, userId));
    }

    @GetMapping("/internal/folders/{folderId}/members")
    public ApiResponse<Set<Long>> members(@PathVariable Long folderId) {
        return ApiResponse.ok(internalFolderService.accessorIds(folderId));
    }

    @GetMapping("/internal/users/{userId}/folders")
    public ApiResponse<List<AccessibleFolderResponse>> folders(@PathVariable Long userId) {
        return ApiResponse.ok(internalFolderService.accessibleFolders(userId));
    }
}
