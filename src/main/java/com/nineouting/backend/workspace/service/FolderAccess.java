package com.nineouting.backend.workspace.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.workspace.domain.Folder;
import com.nineouting.backend.workspace.domain.FolderMember;
import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.error.WorkspaceErrorCode;
import com.nineouting.backend.workspace.repository.FolderMemberRepository;
import com.nineouting.backend.workspace.repository.FolderRepository;
import com.nineouting.backend.workspace.repository.WorkspaceMemberRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 워크스페이스·폴더 접근 규칙 (docs/requirements.md "권한", docs/services/workspace-service.md 3장).
 * <ul>
 *   <li>기본 폴더: 워크스페이스 멤버 누구나</li>
 *   <li>그 외 폴더: 만든 사람(소유자) + 초대된 멤버 + (하위 폴더면) 상위 폴더에 들어갈 수 있는 사람</li>
 *   <li>관리자라도 초대받지 않은 폴더에는 들어갈 수 없다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FolderAccess {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final FolderRepository folderRepository;
    private final FolderMemberRepository folderMemberRepository;

    public WorkspaceMember requireMember(Long workspaceId, Long userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.NOT_WORKSPACE_MEMBER));
    }

    public WorkspaceMember requireAdmin(Long workspaceId, Long userId) {
        WorkspaceMember member = requireMember(workspaceId, userId);
        if (!member.isAdmin()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return member;
    }

    public Folder requireFolder(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 폴더에 들어갈 수 없으면 예외. 워크스페이스 멤버가 아니면 NOT_WORKSPACE_MEMBER, 멤버지만 권한이 없으면 FORBIDDEN. */
    public Folder requireAccess(Long folderId, Long userId) {
        Folder folder = requireFolder(folderId);
        requireMember(folder.getWorkspaceId(), userId);
        if (!canAccess(folder, userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return folder;
    }

    public Folder requireOwner(Long folderId, Long userId) {
        Folder folder = requireFolder(folderId);
        requireMember(folder.getWorkspaceId(), userId);
        if (!folder.isOwnedBy(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return folder;
    }

    public boolean canAccess(Folder folder, Long userId) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(folder.getWorkspaceId(), userId)) {
            return false;
        }
        Snapshot snapshot = snapshot(folder.getWorkspaceId());
        return snapshot.canAccess(folder, userId);
    }

    /** 한 워크스페이스의 폴더·초대 정보를 한 번에 읽어 여러 폴더의 권한을 계산한다. */
    public Snapshot snapshot(Long workspaceId) {
        List<Folder> folders = folderRepository.findByWorkspaceId(workspaceId);
        Map<Long, Folder> byId = folders.stream().collect(Collectors.toMap(Folder::getId, Function.identity()));
        Map<Long, List<FolderMember>> invited = folderMemberRepository.findByFolderIdIn(byId.keySet()).stream()
                .collect(Collectors.groupingBy(FolderMember::getFolderId));
        Set<Long> workspaceMembers = workspaceMemberRepository.findByWorkspaceIdOrderByJoinedAtAsc(workspaceId).stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Snapshot(folders, byId, invited, workspaceMembers);
    }

    public record Snapshot(List<Folder> folders, Map<Long, Folder> byId, Map<Long, List<FolderMember>> invited,
                           Set<Long> workspaceMembers) {

        public boolean canAccess(Folder folder, Long userId) {
            return accessors(folder).contains(userId);
        }

        /** 폴더에 들어갈 수 있는 사용자 전체 (워크스페이스 멤버 중에서). */
        public Set<Long> accessors(Folder folder) {
            if (folder.isDefaultFolder()) {
                return workspaceMembers;
            }
            Set<Long> result = new LinkedHashSet<>();
            if (folder.getOwnerUserId() != null) {
                result.add(folder.getOwnerUserId());
            }
            invited.getOrDefault(folder.getId(), List.of()).forEach(member -> result.add(member.getUserId()));
            if (folder.getParentId() != null && byId.containsKey(folder.getParentId())) {
                result.addAll(accessors(byId.get(folder.getParentId())));
            }
            result.retainAll(workspaceMembers);
            return result;
        }
    }
}
