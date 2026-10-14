package com.nineouting.backend.workspace.service;

import com.nineouting.backend.workspace.domain.Folder;
import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.dto.AccessibleFolderResponse;
import com.nineouting.backend.workspace.dto.FolderAccessResponse;
import com.nineouting.backend.workspace.repository.WorkspaceMemberRepository;
import com.nineouting.backend.workspace.service.FolderAccess.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 서비스(meeting·minutes)가 폴더 권한을 확인할 때 쓴다 (docs/services/workspace-service.md 4.5).
 */
@Service
@RequiredArgsConstructor
public class InternalFolderService {

    private final FolderAccess folderAccess;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public FolderAccessResponse access(Long folderId, Long userId) {
        Folder folder = folderAccess.requireFolder(folderId);
        return new FolderAccessResponse(folderId, folder.getWorkspaceId(), folderAccess.canAccess(folder, userId));
    }

    /** 회의 참석자 선택 (REQ-058): 폴더에 들어갈 수 있는 사람 전체 */
    @Transactional(readOnly = true)
    public Set<Long> accessorIds(Long folderId) {
        Folder folder = folderAccess.requireFolder(folderId);
        return folderAccess.snapshot(folder.getWorkspaceId()).accessors(folder);
    }

    /** 홈 캘린더 (REQ-018): 사용자가 들어갈 수 있는 모든 워크스페이스의 폴더 */
    @Transactional(readOnly = true)
    public List<AccessibleFolderResponse> accessibleFolders(Long userId) {
        List<AccessibleFolderResponse> result = new ArrayList<>();
        for (WorkspaceMember membership : workspaceMemberRepository.findByUserId(userId)) {
            Snapshot snapshot = folderAccess.snapshot(membership.getWorkspaceId());
            snapshot.folders().stream()
                    .filter(folder -> snapshot.canAccess(folder, userId))
                    .forEach(folder -> result.add(new AccessibleFolderResponse(folder.getId(), folder.getWorkspaceId(),
                            folder.getParentId(), folder.getName())));
        }
        return result;
    }
}
