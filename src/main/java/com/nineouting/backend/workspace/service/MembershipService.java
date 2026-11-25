package com.nineouting.backend.workspace.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.workspace.MemberJoinApproved;
import com.nineouting.backend.common.event.workspace.MemberJoinRequested;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.workspace.config.WorkspaceProperties;
import com.nineouting.backend.workspace.domain.Folder;
import com.nineouting.backend.workspace.domain.FolderAccessScope;
import com.nineouting.backend.workspace.domain.FolderMember;
import com.nineouting.backend.workspace.domain.InviteLink;
import com.nineouting.backend.workspace.domain.JoinRequest;
import com.nineouting.backend.workspace.domain.JoinRequestStatus;
import com.nineouting.backend.workspace.domain.FolderRecord;
import com.nineouting.backend.workspace.domain.Workspace;
import com.nineouting.backend.workspace.repository.FolderRecordRepository;
import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.domain.WorkspaceRole;
import com.nineouting.backend.workspace.dto.InviteInfoResponse;
import com.nineouting.backend.workspace.dto.InvitePreviewResponse;
import com.nineouting.backend.workspace.dto.InviteLinkResponse;
import com.nineouting.backend.workspace.dto.JoinRequestResponse;
import com.nineouting.backend.workspace.dto.MemberResponse;
import com.nineouting.backend.workspace.dto.OwnershipResponse;
import com.nineouting.backend.workspace.repository.FavoriteRepository;
import com.nineouting.backend.workspace.dto.WorkspaceResponse;
import com.nineouting.backend.workspace.error.WorkspaceErrorCode;
import com.nineouting.backend.workspace.repository.FolderMemberRepository;
import com.nineouting.backend.workspace.repository.FolderRepository;
import com.nineouting.backend.workspace.repository.FolderVisitRepository;
import com.nineouting.backend.workspace.repository.InviteLinkRepository;
import com.nineouting.backend.workspace.repository.JoinRequestRepository;
import com.nineouting.backend.workspace.repository.LastWorkspaceRepository;
import com.nineouting.backend.workspace.repository.WorkspaceMemberRepository;
import com.nineouting.backend.workspace.repository.WorkspaceRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크·참여 요청 (REQ-015·034, 016~017·035 레거시), 멤버 관리 (REQ-033, 036), 나가기 (REQ-032).
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private static final int TOKEN_BYTES = 32;
    private static final String STATUS_MEMBER = "MEMBER";
    private static final String STATUS_NONE = "NONE";

    private final WorkspaceRepository workspaceRepository;
    private final FolderRecordRepository folderRecordRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final FolderRepository folderRepository;
    private final FolderMemberRepository folderMemberRepository;
    private final FolderVisitRepository folderVisitRepository;
    private final LastWorkspaceRepository lastWorkspaceRepository;
    private final FavoriteRepository favoriteRepository;
    private final FolderAccess folderAccess;
    private final AccessibleFolderCache accessibleFolderCache;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkspaceProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 소유자. 새로 발급하면 이전 링크는 무효가 된다. 유효 기간은 1일. */
    @Transactional
    public InviteLinkResponse issueInviteLink(Long workspaceId, Long ownerUserId) {
        folderAccess.requireWorkspaceOwner(workspaceId, ownerUserId);
        String token = newToken();
        LocalDateTime expiresAt = now().plus(properties.inviteLinkTtl());
        inviteLinkRepository.findByWorkspaceId(workspaceId).ifPresentOrElse(
                link -> link.reissue(token, expiresAt, ownerUserId),
                () -> inviteLinkRepository.save(new InviteLink(workspaceId, token, expiresAt, ownerUserId)));
        return new InviteLinkResponse(token, properties.frontendBaseUrl() + "/invites/" + token, expiresAt);
    }

    @Transactional(readOnly = true)
    public InviteInfoResponse inviteInfo(String token, Long userId) {
        InviteLink link = validLink(token);
        Workspace workspace = workspaceOf(link);
        String status;
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), userId)) {
            status = STATUS_MEMBER;
        } else {
            status = joinRequestRepository.findFirstByWorkspaceIdAndUserIdOrderByIdDesc(workspace.getId(), userId)
                    .map(request -> request.getStatus().name())
                    .orElse(STATUS_NONE);
        }
        UserSummary inviter = userClient.findUsers(List.of(link.getCreatedBy())).get(link.getCreatedBy());
        return new InviteInfoResponse(workspace.getId(), workspace.getName(), inviter == null ? null : inviter.name(),
                status);
    }

    /** 로그인 전 초대 첫 화면. 만료된 링크면 INVITE_LINK_INVALID */
    public InvitePreviewResponse invitePreview(String token) {
        InviteLink link = validLink(token);
        UserSummary inviter = userClient.findUsers(List.of(link.getCreatedBy())).get(link.getCreatedBy());
        return new InvitePreviewResponse(workspaceOf(link).getName(), inviter == null ? null : inviter.name());
    }

    /**
     * REQ-015 초대 링크로 바로 참여 (2026-10-28 기획서에서 참여 요청·승인 REQ-016~017·035가 빠짐).
     * 링크가 유효하면 승인 없이 멤버가 된다. 이미 멤버면 그대로 돌려준다.
     */
    @Transactional
    public WorkspaceResponse joinByLink(String token, Long userId) {
        Workspace workspace = workspaceOf(validLink(token));
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId)
                .orElseGet(() -> workspaceMemberRepository.save(
                        new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.MEMBER, now())));
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), member.getRole(), false);
    }

    /** REQ-016 (레거시). 소유자에게 알림 이벤트를 보낸다. 거절된 뒤에는 다시 요청할 수 있다. */
    @Transactional
    public JoinRequestResponse requestJoin(String token, Long userId) {
        Workspace workspace = workspaceOf(validLink(token));
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), userId)) {
            throw new BusinessException(WorkspaceErrorCode.ALREADY_MEMBER);
        }
        if (joinRequestRepository.existsByWorkspaceIdAndUserIdAndStatus(workspace.getId(), userId, JoinRequestStatus.PENDING)) {
            throw new BusinessException(WorkspaceErrorCode.JOIN_REQUEST_PENDING);
        }
        JoinRequest request = joinRequestRepository.save(new JoinRequest(workspace.getId(), userId, now()));
        Long ownerUserId = ownerOf(workspace.getId()).getUserId();
        outboxEventWriter.append(MemberJoinRequested.TOPIC, workspace.getId().toString(), MemberJoinRequested.TYPE,
                new MemberJoinRequested(workspace.getId(), workspace.getName(), request.getId(), userId, ownerUserId));
        return new JoinRequestResponse(request.getId(), userId, null, null, request.getStatus(), request.getRequestedAt());
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> pendingJoinRequests(Long workspaceId, Long ownerUserId) {
        folderAccess.requireWorkspaceOwner(workspaceId, ownerUserId);
        List<JoinRequest> requests = joinRequestRepository
                .findByWorkspaceIdAndStatusOrderByRequestedAtAsc(workspaceId, JoinRequestStatus.PENDING);
        Map<Long, UserSummary> users = userClient.findUsers(requests.stream().map(JoinRequest::getUserId).toList());
        return requests.stream().map(request -> {
            UserSummary user = users.get(request.getUserId());
            return new JoinRequestResponse(request.getId(), request.getUserId(), user == null ? null : user.name(),
                    user == null ? null : user.email(), request.getStatus(), request.getRequestedAt());
        }).toList();
    }

    /** REQ-035 (레거시). 승인하면 멤버가 되고 요청자에게 알림 이벤트를 보낸다. */
    @Transactional
    public void approve(Long workspaceId, Long joinRequestId, Long ownerUserId) {
        JoinRequest request = decidablePendingRequest(workspaceId, joinRequestId, ownerUserId);
        request.decide(JoinRequestStatus.APPROVED, ownerUserId, now());
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, request.getUserId())) {
            workspaceMemberRepository.save(new WorkspaceMember(workspaceId, request.getUserId(), WorkspaceRole.MEMBER, now()));
        }
        Workspace workspace = workspaceOf(workspaceId);
        outboxEventWriter.append(MemberJoinApproved.TOPIC, workspaceId.toString(), MemberJoinApproved.TYPE,
                new MemberJoinApproved(workspaceId, workspace.getName(), request.getUserId()));
    }

    /** REQ-017 (레거시). 요청자는 초대 화면에서 REJECTED 상태를 보고 다시 요청할 수 있다. */
    @Transactional
    public void reject(Long workspaceId, Long joinRequestId, Long ownerUserId) {
        decidablePendingRequest(workspaceId, joinRequestId, ownerUserId)
                .decide(JoinRequestStatus.REJECTED, ownerUserId, now());
    }

    /** REQ-033. 멤버 누구나 본다 (관리는 소유자만, 2026-10-26 결정). */
    // 다른 서비스 응답을 기다리는 동안 DB 연결을 잡고 있지 않는다. 조회마다 짧은 읽기 트랜잭션을 쓴다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MemberResponse> members(Long workspaceId, Long userId) {
        folderAccess.requireMember(workspaceId, userId);
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceIdOrderByJoinedAtAsc(workspaceId);
        Map<Long, UserSummary> users = userClient.findUsers(members.stream().map(WorkspaceMember::getUserId).toList(), workspaceId);
        return members.stream().map(member -> {
            UserSummary user = users.get(member.getUserId());
            return new MemberResponse(member.getUserId(), user == null ? null : user.name(),
                    user == null ? null : user.email(), user == null ? null : user.profileImageUrl(),
                    member.getRole(), member.getFolderAccess(), member.getJoinedAt());
        }).toList();
    }

    /** REQ-128. 소유자만 바꾼다 */
    @Transactional
    public void changeFolderAccess(Long workspaceId, Long ownerUserId, Long targetUserId, FolderAccessScope scope) {
        folderAccess.requireWorkspaceOwner(workspaceId, ownerUserId);
        folderAccess.requireMember(workspaceId, targetUserId).changeFolderAccess(scope);
        // REQ-198 워크스페이스 시작하기: 폴더 권한 정하기를 마친 것으로 둔다
        workspaceRepository.findById(workspaceId).ifPresent(Workspace::markFolderAccessConfigured);
    }

    /** REQ-036 내보내기. 내보낸 사람이 가진 폴더는 자동으로 양도한다. */
    @Transactional
    public void removeMember(Long workspaceId, Long ownerUserId, Long targetUserId) {
        folderAccess.requireWorkspaceOwner(workspaceId, ownerUserId);
        if (ownerUserId.equals(targetUserId)) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_CANNOT_LEAVE);
        }
        WorkspaceMember target = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        transferOwnedFolders(workspaceId, targetUserId, ownerUserId);
        transferTrashedFolders(workspaceId, targetUserId, ownerUserId);
        leaveWorkspace(target);
    }

    /** REQ-036 소유자 양도. 소유자는 워크스페이스마다 1명이므로 넘기는 사람은 일반 멤버가 된다. */
    @Transactional
    public void transferOwnership(Long workspaceId, Long ownerUserId, Long targetUserId) {
        WorkspaceMember owner = folderAccess.requireWorkspaceOwner(workspaceId, ownerUserId);
        if (ownerUserId.equals(targetUserId)) {
            return;
        }
        WorkspaceMember target = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        owner.changeRole(WorkspaceRole.MEMBER);
        target.changeRole(WorkspaceRole.OWNER);
    }

    /** REQ-032. 소유자는 양도한 뒤에, 폴더 소유자는 폴더를 양도한 뒤에 나갈 수 있다 (쓰레기통 폴더는 자동으로 넘긴다). */
    @Transactional
    public void leave(Long workspaceId, Long userId) {
        WorkspaceMember member = folderAccess.requireMember(workspaceId, userId);
        if (member.isOwner()) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_CANNOT_LEAVE);
        }
        if (!folderRepository.findByWorkspaceIdAndOwnerUserId(workspaceId, userId).isEmpty()) {
            throw new BusinessException(WorkspaceErrorCode.FOLDER_OWNERSHIP_REMAINING);
        }
        transferTrashedFolders(workspaceId, userId, ownerOf(workspaceId).getUserId());
        leaveWorkspace(member);
    }

    /** REQ-207 탈퇴 전 확인: 내가 소유한 워크스페이스와 폴더 (소유권을 넘겨야 탈퇴할 수 있다) */
    @Transactional(readOnly = true)
    public OwnershipResponse ownership(Long userId) {
        List<String> workspaces = new java.util.ArrayList<>();
        List<String> folders = new java.util.ArrayList<>();
        for (WorkspaceMember member : workspaceMemberRepository.findByUserId(userId)) {
            Workspace workspace = workspaceOf(member.getWorkspaceId());
            if (member.isOwner()) {
                workspaces.add(workspace.getName());
            }
            for (Folder folder : folderRepository.findByWorkspaceIdAndOwnerUserId(workspace.getId(), userId)) {
                folders.add(workspace.getName() + " / " + folder.getName());
            }
        }
        return new OwnershipResponse(workspaces, folders);
    }

    /**
     * REQ-207 탈퇴: 모든 워크스페이스에서 나가고 즐겨찾기를 지운다. 소유한 것이 남아 있으면 아무것도 바꾸지 않고 거절한다.
     * 쓰레기통 폴더는 나가기(REQ-032)처럼 자동으로 넘긴다
     */
    @Transactional
    public void leaveAll(Long userId) {
        if (!ownership(userId).ownsNothing()) {
            throw new BusinessException(WorkspaceErrorCode.WITHDRAW_OWNERSHIP_REMAINING);
        }
        for (WorkspaceMember member : workspaceMemberRepository.findByUserId(userId)) {
            transferTrashedFolders(member.getWorkspaceId(), userId, ownerOf(member.getWorkspaceId()).getUserId());
            leaveWorkspace(member);
        }
        favoriteRepository.deleteAll(favoriteRepository.findByUserIdOrderByCreatedAtAsc(userId));
    }

    /**
     * 떠나는 사람이 가진 쓰레기통 폴더도 넘긴다. 그러지 않으면 복구했을 때 워크스페이스에 없는 사람이 소유자로 남는다 (2026-11-17).
     * 넘길 사람은 살아 있는 폴더와 같다: 가장 먼저 초대된 폴더 멤버, 없으면 워크스페이스 소유자
     */
    private void transferTrashedFolders(Long workspaceId, Long leavingUserId, Long ownerUserId) {
        for (Long folderId : folderRecordRepository.findTrashedIdsOwnedBy(workspaceId, leavingUserId)) {
            Optional<FolderMember> next = folderMemberRepository.findByFolderIdOrderByInvitedAtAscIdAsc(folderId).stream()
                    .filter(member -> !member.getUserId().equals(leavingUserId))
                    .findFirst();
            Long newOwner = next.map(FolderMember::getUserId).orElse(ownerUserId);
            folderMemberRepository.findByFolderIdAndUserId(folderId, newOwner).ifPresent(folderMemberRepository::delete);
            folderRecordRepository.changeOwner(folderId, newOwner);
            accessibleFolderCache.changed();  // 일괄 수정은 엔티티 리스너를 거치지 않는다
        }
    }

    /**
     * 양도 없이 빠지는 사람의 폴더: 가장 먼저 초대된 폴더 멤버에게, 멤버가 없으면 소유자에게 넘긴다.
     */
    private void transferOwnedFolders(Long workspaceId, Long leavingUserId, Long ownerUserId) {
        for (Folder folder : folderRepository.findByWorkspaceIdAndOwnerUserId(workspaceId, leavingUserId)) {
            Optional<FolderMember> next = folderMemberRepository.findByFolderIdOrderByInvitedAtAscIdAsc(folder.getId())
                    .stream()
                    .filter(member -> !member.getUserId().equals(leavingUserId))
                    .findFirst();
            Long newOwner = next.map(FolderMember::getUserId).orElse(ownerUserId);
            folderMemberRepository.findByFolderIdAndUserId(folder.getId(), newOwner).ifPresent(folderMemberRepository::delete);
            folder.transferOwner(newOwner);
        }
    }

    private void leaveWorkspace(WorkspaceMember member) {
        Long workspaceId = member.getWorkspaceId();
        Long userId = member.getUserId();
        // 쓰레기통 폴더의 멤버 기록도 지운다 (복구했을 때 떠난 사람이 멤버로 남지 않게, 2026-11-17)
        List<Long> folderIds = folderRecordRepository.findByWorkspaceId(workspaceId).stream().map(FolderRecord::getId).toList();
        if (!folderIds.isEmpty()) {
            folderMemberRepository.deleteByFolderIdInAndUserId(folderIds, userId);
            folderVisitRepository.deleteByFolderIdInAndUserId(folderIds, userId);
        }
        lastWorkspaceRepository.findById(userId)
                .filter(last -> last.getWorkspaceId().equals(workspaceId))
                .ifPresent(lastWorkspaceRepository::delete);
        workspaceMemberRepository.delete(member);
    }

    private JoinRequest decidablePendingRequest(Long workspaceId, Long joinRequestId, Long ownerUserId) {
        folderAccess.requireWorkspaceOwner(workspaceId, ownerUserId);
        JoinRequest request = joinRequestRepository.findById(joinRequestId)
                .filter(found -> found.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!request.isPending()) {
            throw new BusinessException(WorkspaceErrorCode.JOIN_REQUEST_ALREADY_DECIDED);
        }
        return request;
    }

    private InviteLink validLink(String token) {
        return inviteLinkRepository.findByToken(token)
                .filter(link -> !link.isExpired(now()))
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.INVITE_LINK_INVALID));
    }

    private Workspace workspaceOf(InviteLink link) {
        return workspaceOf(link.getWorkspaceId());
    }

    private Workspace workspaceOf(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private WorkspaceMember ownerOf(Long workspaceId) {
        return workspaceMemberRepository.findByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER)
                .orElseThrow(() -> new IllegalStateException("소유자가 없는 워크스페이스: " + workspaceId));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
