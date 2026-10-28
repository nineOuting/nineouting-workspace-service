package com.nineouting.backend.workspace.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.workspace.FolderDeleted;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.workspace.config.WorkspaceProperties;
import com.nineouting.backend.workspace.domain.FavoriteType;
import com.nineouting.backend.workspace.domain.FolderRecord;
import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.domain.WorkspaceRole;
import com.nineouting.backend.workspace.dto.TrashFolderResponse;
import com.nineouting.backend.workspace.error.WorkspaceErrorCode;
import com.nineouting.backend.workspace.repository.FavoriteRepository;
import com.nineouting.backend.workspace.repository.FolderMemberRepository;
import com.nineouting.backend.workspace.repository.FolderRecordRepository;
import com.nineouting.backend.workspace.repository.FolderVisitRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 쓰레기통 (REQ-143, 2026-10-28 기획서). 지금은 폴더만 들어온다 (2026-10-28 결정).
 * 지운 사람은 자기가 지운 것을, 워크스페이스 소유자는 워크스페이스 안 전부를 보고 복구·영구 삭제한다.
 * 보관 기간(기본 3개월)이 지나면 매일 새벽 자동으로 영구 삭제한다. 영구 삭제할 때 비로소 FolderDeleted를 보내
 * meeting·minutes가 안의 회의·회의록을 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashService {

    private final FolderRecordRepository folderRecordRepository;
    private final FolderMemberRepository folderMemberRepository;
    private final FolderVisitRepository folderVisitRepository;
    private final FavoriteRepository favoriteRepository;
    private final FolderAccess folderAccess;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkspaceProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TrashFolderResponse> list(Long workspaceId, Long userId) {
        boolean owner = isOwner(folderAccess.requireMember(workspaceId, userId));
        List<FolderRecord> folders = folderRecordRepository
                .findByWorkspaceIdAndDeletedAtIsNotNullAndParentIdIsNullOrderByDeletedAtDescIdDesc(workspaceId).stream()
                .filter(folder -> owner || userId.equals(folder.getDeletedByUserId()))
                .toList();
        Map<Long, UserSummary> users = userClient.findUsers(folders.stream()
                .map(FolderRecord::getDeletedByUserId).filter(Objects::nonNull).distinct().toList(), workspaceId);
        return folders.stream().map(folder -> {
            UserSummary deletedBy = users.get(folder.getDeletedByUserId());
            return new TrashFolderResponse(folder.getId(), folder.getName(), folder.getDeletedAt(),
                    folder.getDeletedByUserId(), deletedBy == null ? null : deletedBy.name(),
                    folder.getDeletedAt().plus(properties.trashRetention()));
        }).toList();
    }

    /** 같은 이름의 폴더가 그사이 생겼으면 복구하지 않는다 (먼저 그 폴더 이름을 바꾸게 한다) */
    @Transactional
    public void restore(Long workspaceId, Long folderId, Long userId) {
        FolderRecord folder = requireManageable(workspaceId, folderId, userId);
        try {
            folderRecordRepository.restore(folder.getId(), folder.getDeletedAt());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(WorkspaceErrorCode.FOLDER_NAME_DUPLICATED);
        }
    }

    @Transactional
    public void purge(Long workspaceId, Long folderId, Long userId) {
        purge(requireManageable(workspaceId, folderId, userId));
    }

    /** 보관 기간이 지난 폴더를 영구 삭제한다. 폴더마다 따로 커밋해서 하나가 실패해도 나머지는 지운다 */
    @Scheduled(cron = "${nineouting.workspace.trash-purge-cron:0 40 4 * * *}")
    public void purgeExpired() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        LocalDateTime before = LocalDateTime.now(clock).minus(properties.trashRetention());
        for (FolderRecord folder : folderRecordRepository.findByDeletedAtBeforeAndParentIdIsNull(before)) {
            try {
                transaction.executeWithoutResult(status -> purge(folder));
            } catch (RuntimeException e) {
                log.warn("쓰레기통 폴더를 영구 삭제하지 못했습니다. 내일 다시 합니다 (folderId={}): {}", folder.getId(), e.toString());
            }
        }
    }

    /** 여러 대가 동시에 지워도 실제로 지운 쪽만 이벤트를 보낸다 */
    private void purge(FolderRecord folder) {
        List<Long> folderIds = folderRecordRepository.findWithChildren(folder.getId()).stream()
                .filter(FolderRecord::isTrashed).map(FolderRecord::getId).toList();
        folderMemberRepository.deleteByFolderIdIn(folderIds);
        folderVisitRepository.deleteByFolderIdIn(folderIds);
        favoriteRepository.deleteByTargetTypeAndTargetIdIn(FavoriteType.FOLDER, folderIds);
        if (folderRecordRepository.deleteTrashed(folderIds) > 0) {
            outboxEventWriter.append(FolderDeleted.TOPIC, folder.getWorkspaceId().toString(), FolderDeleted.TYPE,
                    new FolderDeleted(folder.getWorkspaceId(), folderIds));
        }
    }

    private FolderRecord requireManageable(Long workspaceId, Long folderId, Long userId) {
        boolean owner = isOwner(folderAccess.requireMember(workspaceId, userId));
        FolderRecord folder = folderRecordRepository.findById(folderId)
                .filter(found -> found.isTrashed() && found.getParentId() == null && found.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!owner && !userId.equals(folder.getDeletedByUserId())) {
            throw new BusinessException(WorkspaceErrorCode.TRASH_FORBIDDEN);
        }
        return folder;
    }

    private static boolean isOwner(WorkspaceMember member) {
        return member.getRole() == WorkspaceRole.OWNER;
    }
}
