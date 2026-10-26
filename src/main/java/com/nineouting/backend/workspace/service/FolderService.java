package com.nineouting.backend.workspace.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.workspace.FolderDeleted;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.workspace.domain.Folder;
import com.nineouting.backend.workspace.domain.FolderMember;
import com.nineouting.backend.workspace.domain.FolderVisit;
import com.nineouting.backend.workspace.domain.Workspace;
import com.nineouting.backend.workspace.dto.FolderDetailResponse;
import com.nineouting.backend.workspace.dto.FolderDetailResponse.PathItem;
import com.nineouting.backend.workspace.dto.FolderMemberResponse;
import com.nineouting.backend.workspace.dto.FolderNode;
import com.nineouting.backend.workspace.dto.RecentFolderResponse;
import com.nineouting.backend.workspace.error.WorkspaceErrorCode;
import com.nineouting.backend.workspace.repository.FolderMemberRepository;
import com.nineouting.backend.workspace.repository.FolderRepository;
import com.nineouting.backend.workspace.repository.FolderVisitRepository;
import com.nineouting.backend.workspace.repository.WorkspaceRepository;
import com.nineouting.backend.workspace.service.FolderAccess.Snapshot;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 폴더·하위 폴더 (REQ-037~047, 058), 최근 프로젝트 (REQ-024).
 */
@Service
@RequiredArgsConstructor
public class FolderService {

    private static final int RECENT_FOLDER_LIMIT = 10;
    /** 접근 권한을 잃은 폴더를 걸러내고도 10개를 채울 수 있게 넉넉히 읽는다. */
    private static final int RECENT_VISIT_READ = 30;
    private static final Comparator<Folder> FOLDER_ORDER = Comparator
            .comparing((Folder folder) -> !folder.isDefaultFolder())
            .thenComparing(Folder::getCreatedAt)
            .thenComparing(Folder::getId);

    private final FolderRepository folderRepository;
    private final FolderMemberRepository folderMemberRepository;
    private final FolderVisitRepository folderVisitRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FolderAccess folderAccess;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    /** REQ-037. 들어갈 수 있는 폴더만, 기본 폴더가 맨 위. 하위 폴더에만 초대됐으면 상위 폴더는 이름만 보인다. */
    @Transactional(readOnly = true)
    public List<FolderNode> list(Long workspaceId, Long userId) {
        folderAccess.requireMember(workspaceId, userId);
        Snapshot snapshot = folderAccess.snapshot(workspaceId);
        List<FolderNode> nodes = new ArrayList<>();
        for (Folder top : snapshot.folders().stream().filter(Folder::isTopLevel).sorted(FOLDER_ORDER).toList()) {
            List<FolderNode> children = snapshot.folders().stream()
                    .filter(child -> top.getId().equals(child.getParentId()))
                    .filter(child -> snapshot.canAccess(child, userId))
                    .sorted(FOLDER_ORDER)
                    .map(child -> node(child, true, List.of()))
                    .toList();
            boolean accessible = snapshot.canAccess(top, userId);
            if (accessible || !children.isEmpty()) {
                nodes.add(node(top, accessible, children));
            }
        }
        return nodes;
    }

    /**
     * REQ-039~040, 046~047. 워크스페이스 멤버 누구나, 이름은 워크스페이스 안에서 겹칠 수 없다.
     * 폴더 안에 폴더는 만들 수 없다 (2026-10-26 기획서 REQ-040). 예전 하위 폴더 구조(parentId)는 읽기만 남겨 둔다.
     */
    @Transactional
    public FolderNode create(Long workspaceId, Long userId, String name, Long parentId) {
        folderAccess.requireMember(workspaceId, userId);
        if (parentId != null) {
            throw new BusinessException(WorkspaceErrorCode.FOLDER_DEPTH_EXCEEDED);
        }
        Folder folder = saveWithUniqueName(Folder.create(workspaceId, null, name, userId));
        return node(folder, true, List.of());
    }

    /** REQ-038. 열 때마다 최근 방문으로 기록한다 (REQ-024). */
    @Transactional
    public FolderDetailResponse open(Long folderId, Long userId) {
        Folder folder = folderAccess.requireAccess(folderId, userId);
        folderVisitRepository.findByUserIdAndFolderId(userId, folderId).ifPresentOrElse(
                visit -> visit.touch(now()),
                () -> folderVisitRepository.save(new FolderVisit(userId, folderId, now())));

        List<PathItem> path = new ArrayList<>();
        if (folder.getParentId() != null) {
            Folder parent = folderAccess.requireFolder(folder.getParentId());
            path.add(new PathItem(parent.getId(), parent.getName(), folderAccess.canAccess(parent, userId)));
        }
        path.add(new PathItem(folder.getId(), folder.getName(), true));
        return new FolderDetailResponse(folder.getId(), folder.getWorkspaceId(), folder.getParentId(), folder.getName(),
                folder.isDefaultFolder(), folder.getOwnerUserId(), folder.isOwnedBy(userId), path);
    }

    /** 소유자만. 기본 폴더는 소유자가 없어 소유자가 바꾼다 (REQ-044). */
    @Transactional
    public FolderNode rename(Long folderId, Long userId, String name) {
        Folder folder = folderAccess.requireFolder(folderId);
        if (folder.isDefaultFolder()) {
            folderAccess.requireWorkspaceOwner(folder.getWorkspaceId(), userId);
        } else {
            folderAccess.requireOwner(folderId, userId);
        }
        folder.rename(name);
        saveWithUniqueName(folder);
        return node(folder, true, List.of());
    }

    /** REQ-058. 소유자만, 하위 폴더까지 함께 지우고 안의 회의·회의록을 지우도록 이벤트를 보낸다. */
    @Transactional
    public void delete(Long folderId, Long userId) {
        Folder folder = folderAccess.requireFolder(folderId);
        if (folder.isDefaultFolder()) {
            throw new BusinessException(WorkspaceErrorCode.DEFAULT_FOLDER_UNDELETABLE);
        }
        folderAccess.requireOwner(folderId, userId);
        List<Folder> targets = new ArrayList<>(folderRepository.findByParentId(folderId));
        targets.add(folder);
        List<Long> folderIds = targets.stream().map(Folder::getId).toList();

        folderMemberRepository.deleteByFolderIdIn(folderIds);
        folderVisitRepository.deleteByFolderIdIn(folderIds);
        folderRepository.deleteAll(targets);
        outboxEventWriter.append(FolderDeleted.TOPIC, folder.getWorkspaceId().toString(), FolderDeleted.TYPE,
                new FolderDeleted(folder.getWorkspaceId(), folderIds));
    }

    /** REQ-041. 들어갈 수 있는 사람 전체. 상위 폴더 멤버라서 들어오는 사람은 inherited. */
    @Transactional(readOnly = true)
    public List<FolderMemberResponse> members(Long folderId, Long userId) {
        Folder folder = folderAccess.requireAccess(folderId, userId);
        Snapshot snapshot = folderAccess.snapshot(folder.getWorkspaceId());
        Set<Long> direct = new LinkedHashSet<>();
        if (folder.isDefaultFolder()) {
            direct.addAll(snapshot.workspaceMembers());
        } else {
            if (folder.getOwnerUserId() != null) {
                direct.add(folder.getOwnerUserId());
            }
            snapshot.invited().getOrDefault(folderId, List.of()).forEach(member -> direct.add(member.getUserId()));
        }
        Set<Long> all = snapshot.accessors(folder);
        Map<Long, UserSummary> users = userClient.findUsers(all);
        return all.stream().map(memberId -> {
            UserSummary user = users.get(memberId);
            return new FolderMemberResponse(memberId, user == null ? null : user.name(),
                    user == null ? null : user.email(), user == null ? null : user.profileImageUrl(),
                    folder.isOwnedBy(memberId), !direct.contains(memberId));
        }).toList();
    }

    /** REQ-041. 워크스페이스 소유자만 (2026-10-26 결정), 같은 워크스페이스 멤버만 초대할 수 있다. */
    @Transactional
    public void invite(Long folderId, Long ownerUserId, Long targetUserId) {
        Folder folder = folderAccess.requireFolder(folderId);
        folderAccess.requireWorkspaceOwner(folder.getWorkspaceId(), ownerUserId);
        if (!folderAccess.snapshot(folder.getWorkspaceId()).workspaceMembers().contains(targetUserId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "같은 워크스페이스 멤버만 초대할 수 있습니다");
        }
        if (folder.isOwnedBy(targetUserId) || folderMemberRepository.existsByFolderIdAndUserId(folderId, targetUserId)) {
            throw new BusinessException(WorkspaceErrorCode.ALREADY_FOLDER_MEMBER);
        }
        folderMemberRepository.save(new FolderMember(folderId, targetUserId, now()));
    }

    /** REQ-042. 워크스페이스 소유자만 (2026-10-26 결정). "모든 폴더" 권한으로 들어오는 사람은 여기서 내보낼 수 없다. */
    @Transactional
    public void removeMember(Long folderId, Long ownerUserId, Long targetUserId) {
        Folder folder = folderAccess.requireFolder(folderId);
        folderAccess.requireWorkspaceOwner(folder.getWorkspaceId(), ownerUserId);
        FolderMember member = folderMemberRepository.findByFolderIdAndUserId(folderId, targetUserId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.NOT_FOLDER_MEMBER));
        folderMemberRepository.delete(member);
    }

    /**
     * REQ-139. 초대받아 들어온 폴더에서 나간다. 폴더 소유자는 먼저 양도해야 한다 (REQ-043, MSG-045).
     * 기본 폴더와, 폴더 접근 권한이 "모든 폴더"라서 들어오는 폴더는 나갈 수 없다.
     */
    @Transactional
    public void leave(Long folderId, Long userId) {
        Folder folder = folderAccess.requireAccess(folderId, userId);
        if (folder.isDefaultFolder()) {
            throw new BusinessException(WorkspaceErrorCode.DEFAULT_FOLDER_UNLEAVABLE);
        }
        if (folder.isOwnedBy(userId)) {
            throw new BusinessException(WorkspaceErrorCode.FOLDER_OWNER_CANNOT_LEAVE);
        }
        FolderMember member = folderMemberRepository.findByFolderIdAndUserId(folderId, userId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.NOT_FOLDER_MEMBER));
        folderMemberRepository.delete(member);
    }

    /** REQ-043. 초대된 폴더 멤버에게만 넘길 수 있고, 넘긴 사람은 일반 폴더 멤버로 남는다. */
    @Transactional
    public void transferOwner(Long folderId, Long ownerUserId, Long targetUserId) {
        Folder folder = folderAccess.requireOwner(folderId, ownerUserId);
        FolderMember target = folderMemberRepository.findByFolderIdAndUserId(folderId, targetUserId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.NOT_FOLDER_MEMBER));
        folderMemberRepository.delete(target);
        folderMemberRepository.save(new FolderMember(folderId, ownerUserId, now()));
        folder.transferOwner(targetUserId);
    }

    /** REQ-024. 지금도 들어갈 수 있는 폴더만, 최근 방문 순. */
    @Transactional(readOnly = true)
    public List<RecentFolderResponse> recent(Long userId) {
        List<FolderVisit> visits = folderVisitRepository.findByUserIdOrderByVisitedAtDesc(userId, PageRequest.of(0, RECENT_VISIT_READ));
        Map<Long, Folder> folders = new HashMap<>();
        folderRepository.findAllById(visits.stream().map(FolderVisit::getFolderId).toList())
                .forEach(folder -> folders.put(folder.getId(), folder));
        Map<Long, Workspace> workspaces = new HashMap<>();
        workspaceRepository.findAllById(folders.values().stream().map(Folder::getWorkspaceId).distinct().toList())
                .forEach(workspace -> workspaces.put(workspace.getId(), workspace));
        return visits.stream()
                .filter(visit -> folders.containsKey(visit.getFolderId()))
                .filter(visit -> folderAccess.canAccess(folders.get(visit.getFolderId()), userId))
                .limit(RECENT_FOLDER_LIMIT)
                .map(visit -> {
                    Folder folder = folders.get(visit.getFolderId());
                    Workspace workspace = workspaces.get(folder.getWorkspaceId());
                    return new RecentFolderResponse(folder.getId(), folder.getName(), workspace.getId(),
                            workspace.getName(), visit.getVisitedAt());
                })
                .toList();
    }

    private Folder saveWithUniqueName(Folder folder) {
        try {
            return folderRepository.saveAndFlush(folder);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(WorkspaceErrorCode.FOLDER_NAME_DUPLICATED);
        }
    }

    private static FolderNode node(Folder folder, boolean accessible, List<FolderNode> children) {
        return new FolderNode(folder.getId(), folder.getName(), folder.isDefaultFolder(), folder.getOwnerUserId(),
                accessible, children);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
