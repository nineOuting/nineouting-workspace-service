package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.FolderRecord;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 쓰레기통 폴더까지 보는 저장소 (REQ-143) */
public interface FolderRecordRepository extends JpaRepository<FolderRecord, Long> {

    /** 워크스페이스의 쓰레기통. 예전 하위 폴더는 상위 폴더와 함께 다루므로 최상위만 */
    List<FolderRecord> findByWorkspaceIdAndDeletedAtIsNotNullAndParentIdIsNullOrderByDeletedAtDescIdDesc(Long workspaceId);

    /** 보관 기간이 지난 쓰레기통 폴더 (최상위만) */
    List<FolderRecord> findByDeletedAtBeforeAndParentIdIsNull(LocalDateTime before);

    /** 폴더와 예전 하위 폴더 (쓰레기통 포함) */
    @Query("select f from FolderRecord f where f.id = :folderId or f.parentId = :folderId")
    List<FolderRecord> findWithChildren(@Param("folderId") Long folderId);

    /** 워크스페이스의 모든 폴더 (쓰레기통 포함) */
    List<FolderRecord> findByWorkspaceId(Long workspaceId);

    /** 함께 쓰레기통에 들어간 폴더(같은 시각)를 되돌린다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FolderRecord f set f.deletedAt = null, f.deletedByUserId = null "
            + "where (f.id = :folderId or f.parentId = :folderId) and f.deletedAt = :deletedAt")
    int restore(@Param("folderId") Long folderId, @Param("deletedAt") LocalDateTime deletedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FolderRecord f where f.id in :folderIds and f.deletedAt is not null")
    int deleteTrashed(@Param("folderIds") Collection<Long> folderIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FolderRecord f where f.workspaceId = :workspaceId")
    void deleteAllInWorkspace(@Param("workspaceId") Long workspaceId);
}
