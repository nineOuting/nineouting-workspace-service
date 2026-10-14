package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.FolderMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderMemberRepository extends JpaRepository<FolderMember, Long> {

    List<FolderMember> findByFolderIdOrderByInvitedAtAscIdAsc(Long folderId);

    List<FolderMember> findByFolderIdIn(Collection<Long> folderIds);

    Optional<FolderMember> findByFolderIdAndUserId(Long folderId, Long userId);

    boolean existsByFolderIdAndUserId(Long folderId, Long userId);

    void deleteByFolderIdIn(Collection<Long> folderIds);

    void deleteByFolderIdInAndUserId(Collection<Long> folderIds, Long userId);
}
