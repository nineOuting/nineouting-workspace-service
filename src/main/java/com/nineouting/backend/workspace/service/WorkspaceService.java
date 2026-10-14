package com.nineouting.backend.workspace.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.workspace.domain.Folder;
import com.nineouting.backend.workspace.domain.LastWorkspace;
import com.nineouting.backend.workspace.domain.Workspace;
import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.domain.WorkspaceRole;
import com.nineouting.backend.workspace.dto.WorkspaceDetailResponse;
import com.nineouting.backend.workspace.dto.WorkspaceResponse;
import com.nineouting.backend.workspace.repository.FolderRepository;
import com.nineouting.backend.workspace.repository.LastWorkspaceRepository;
import com.nineouting.backend.workspace.repository.WorkspaceMemberRepository;
import com.nineouting.backend.workspace.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워크스페이스 생성·목록·전환 (REQ-028~031, REQ-042).
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final FolderRepository folderRepository;
    private final LastWorkspaceRepository lastWorkspaceRepository;
    private final FolderAccess folderAccess;
    private final Clock clock;

    /** 만든 사람이 관리자가 되고, 기본 폴더가 자동으로 생긴다. */
    @Transactional
    public WorkspaceResponse create(Long userId, String name) {
        Workspace workspace = workspaceRepository.save(new Workspace(name));
        workspaceMemberRepository.save(new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.ADMIN, now()));
        folderRepository.save(Folder.createDefault(workspace.getId()));
        rememberLastVisited(userId, workspace.getId());
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), WorkspaceRole.ADMIN, true);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> findMine(Long userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(userId);
        Map<Long, Workspace> workspaces = workspaceRepository
                .findAllById(memberships.stream().map(WorkspaceMember::getWorkspaceId).toList()).stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));
        Long lastVisited = lastWorkspaceRepository.findById(userId).map(LastWorkspace::getWorkspaceId).orElse(null);
        return memberships.stream()
                .sorted(Comparator.comparing(WorkspaceMember::getJoinedAt))
                .map(member -> {
                    Workspace workspace = workspaces.get(member.getWorkspaceId());
                    return new WorkspaceResponse(workspace.getId(), workspace.getName(), member.getRole(),
                            workspace.getId().equals(lastVisited));
                })
                .toList();
    }

    /** 멤버가 아니면 NOT_WORKSPACE_MEMBER(403) — 프론트는 권한 요청 화면으로 보낸다 (REQ-028). */
    @Transactional(readOnly = true)
    public WorkspaceDetailResponse get(Long workspaceId, Long userId) {
        WorkspaceMember member = folderAccess.requireMember(workspaceId, userId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new WorkspaceDetailResponse(workspace.getId(), workspace.getName(), member.getRole(),
                workspaceMemberRepository.countByWorkspaceId(workspaceId));
    }

    @Transactional
    public void markLastVisited(Long workspaceId, Long userId) {
        folderAccess.requireMember(workspaceId, userId);
        rememberLastVisited(userId, workspaceId);
    }

    private void rememberLastVisited(Long userId, Long workspaceId) {
        lastWorkspaceRepository.findById(userId).ifPresentOrElse(
                last -> last.change(workspaceId),
                () -> lastWorkspaceRepository.save(new LastWorkspace(userId, workspaceId)));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
