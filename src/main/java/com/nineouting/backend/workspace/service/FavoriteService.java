package com.nineouting.backend.workspace.service;

import com.nineouting.backend.workspace.domain.Favorite;
import com.nineouting.backend.workspace.domain.FavoriteType;
import com.nineouting.backend.workspace.domain.Folder;
import com.nineouting.backend.workspace.domain.Workspace;
import com.nineouting.backend.workspace.dto.FavoriteResponse;
import com.nineouting.backend.workspace.repository.FavoriteRepository;
import com.nineouting.backend.workspace.repository.FolderRepository;
import com.nineouting.backend.workspace.repository.WorkspaceMemberRepository;
import com.nineouting.backend.workspace.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 워크스페이스·폴더 즐겨찾기 (REQ-143~144). 등록·해제는 여러 번 해도 결과가 같다 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final FolderRepository folderRepository;
    private final FolderAccess folderAccess;
    private final Clock clock;

    /** 멤버인 워크스페이스만 */
    @Transactional
    public void addWorkspace(Long workspaceId, Long userId) {
        folderAccess.requireMember(workspaceId, userId);
        add(userId, FavoriteType.WORKSPACE, workspaceId);
    }

    /** 들어갈 수 있는 폴더만 */
    @Transactional
    public void addFolder(Long folderId, Long userId) {
        folderAccess.requireAccess(folderId, userId);
        add(userId, FavoriteType.FOLDER, folderId);
    }

    @Transactional
    public void remove(FavoriteType type, Long targetId, Long userId) {
        favoriteRepository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
                .ifPresent(favoriteRepository::delete);
    }

    /** REQ-143. 등록한 순서대로. 지워졌거나 더는 들어갈 수 없는 대상은 뺀다 */
    @Transactional(readOnly = true)
    public List<FavoriteResponse> list(Long userId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<Long, Folder> folders = new HashMap<>();
        folderRepository.findAllById(idsOf(favorites, FavoriteType.FOLDER))
                .forEach(folder -> folders.put(folder.getId(), folder));
        Map<Long, Workspace> workspaces = new HashMap<>();
        List<Long> workspaceIds = new ArrayList<>(idsOf(favorites, FavoriteType.WORKSPACE));
        folders.values().forEach(folder -> workspaceIds.add(folder.getWorkspaceId()));
        workspaceRepository.findAllById(workspaceIds.stream().distinct().toList())
                .forEach(workspace -> workspaces.put(workspace.getId(), workspace));

        return favorites.stream()
                .map(favorite -> favorite.getTargetType() == FavoriteType.WORKSPACE
                        ? workspaceFavorite(workspaces.get(favorite.getTargetId()), userId)
                        : folderFavorite(folders.get(favorite.getTargetId()), workspaces, userId))
                .filter(Objects::nonNull)
                .toList();
    }

    private void add(Long userId, FavoriteType type, Long targetId) {
        if (favoriteRepository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId).isEmpty()) {
            favoriteRepository.save(new Favorite(userId, type, targetId, LocalDateTime.now(clock)));
        }
    }

    private FavoriteResponse workspaceFavorite(Workspace workspace, Long userId) {
        if (workspace == null || !workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), userId)) {
            return null;
        }
        return new FavoriteResponse(FavoriteType.WORKSPACE, workspace.getId(), workspace.getName(), null, null);
    }

    private FavoriteResponse folderFavorite(Folder folder, Map<Long, Workspace> workspaces, Long userId) {
        if (folder == null || !workspaces.containsKey(folder.getWorkspaceId()) || !folderAccess.canAccess(folder, userId)) {
            return null;
        }
        Workspace workspace = workspaces.get(folder.getWorkspaceId());
        return new FavoriteResponse(FavoriteType.FOLDER, workspace.getId(), workspace.getName(), folder.getId(),
                folder.getName());
    }

    private static List<Long> idsOf(List<Favorite> favorites, FavoriteType type) {
        return favorites.stream().filter(favorite -> favorite.getTargetType() == type).map(Favorite::getTargetId).toList();
    }
}
