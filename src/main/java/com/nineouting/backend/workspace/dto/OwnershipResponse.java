package com.nineouting.backend.workspace.dto;

import java.util.List;

/**
 * REQ-207 탈퇴 전에 확인하는 내가 소유한 것. 둘 다 비어 있어야 탈퇴할 수 있다
 *
 * @param workspaces 소유한 워크스페이스 이름
 * @param folders    소유한 프로젝트 폴더 이름 ("워크스페이스 / 폴더")
 */
public record OwnershipResponse(List<String> workspaces, List<String> folders) {

    public boolean ownsNothing() {
        return workspaces.isEmpty() && folders.isEmpty();
    }
}
