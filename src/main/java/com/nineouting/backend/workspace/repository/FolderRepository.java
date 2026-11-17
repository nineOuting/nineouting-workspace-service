package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.Folder;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByWorkspaceId(Long workspaceId);

    /** REQ-202 새 폴더 색을 정할 때 (쓰레기통 폴더는 빠진다) */
    long countByWorkspaceId(Long workspaceId);

    List<Folder> findByWorkspaceIdIn(Collection<Long> workspaceIds);

    List<Folder> findByParentId(Long parentId);

    List<Folder> findByWorkspaceIdAndOwnerUserId(Long workspaceId, Long ownerUserId);
}
