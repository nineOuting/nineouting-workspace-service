package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.FolderVisit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderVisitRepository extends JpaRepository<FolderVisit, Long> {

    Optional<FolderVisit> findByUserIdAndFolderId(Long userId, Long folderId);

    List<FolderVisit> findByUserIdOrderByVisitedAtDesc(Long userId, Pageable page);

    void deleteByFolderIdIn(Collection<Long> folderIds);

    void deleteByFolderIdInAndUserId(Collection<Long> folderIds, Long userId);
}
