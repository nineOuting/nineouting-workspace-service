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
import com.nineouting.backend.workspace.domain.FolderMember;
import com.nineouting.backend.workspace.domain.InviteLink;
import com.nineouting.backend.workspace.domain.JoinRequest;
import com.nineouting.backend.workspace.domain.JoinRequestStatus;
import com.nineouting.backend.workspace.domain.Workspace;
import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.domain.WorkspaceRole;
import com.nineouting.backend.workspace.dto.InviteInfoResponse;
import com.nineouting.backend.workspace.dto.InviteLinkResponse;
import com.nineouting.backend.workspace.dto.JoinRequestResponse;
import com.nineouting.backend.workspace.dto.MemberResponse;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크·참여 요청 (REQ-015~017, 034~035), 멤버 관리 (REQ-033, 036), 나가기 (REQ-032).
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private static final int TOKEN_BYTES = 32;
    private static final String STATUS_MEMBER = "MEMBER";
    private static final String STATUS_NONE = "NONE";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final FolderRepository folderRepository;
    private final FolderMemberRepository folderMemberRepository;
    private final FolderVisitRepository folderVisitRepository;
    private final LastWorkspaceRepository lastWorkspaceRepository;
    private final FolderAccess folderAccess;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkspaceProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 관리자. 새로 발급하면 이전 링크는 무효가 된다. 유효 기간은 1일. */
    @Transactional
    public InviteLinkResponse issueInviteLink(Long workspaceId, Long adminUserId) {
        folderAccess.requireAdmin(workspaceId, adminUserId);
        String token = newToken();
        LocalDateTime expiresAt = now().plus(properties.inviteLinkTtl());
        inviteLinkRepository.findByWorkspaceId(workspaceId).ifPresentOrElse(
                link -> link.reissue(token, expiresAt, adminUserId),
                () -> inviteLinkRepository.save(new InviteLink(workspaceId, token, expiresAt, adminUserId)));
        return new InviteLinkResponse(token, properties.frontendBaseUrl() + "/invites/" + token, expiresAt);
    }

    @Transactional(readOnly = true)
    public InviteInfoResponse inviteInfo(String token, Long userId) {
        Workspace workspace = workspaceOf(validLink(token));
        String status;
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), userId)) {
            status = STATUS_MEMBER;
        } else {
            status = joinRequestRepository.findFirstByWorkspaceIdAndUserIdOrderByIdDesc(workspace.getId(), userId)
                    .map(request -> request.getStatus().name())
                    .orElse(STATUS_NONE);
        }
        return new InviteInfoResponse(workspace.getId(), workspace.getName(), status);
    }

    /** REQ-016. 관리자에게 알림 이벤트를 보낸다. 거절된 뒤에는 다시 요청할 수 있다. */
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
        Long adminUserId = adminOf(workspace.getId()).getUserId();
        outboxEventWriter.append(MemberJoinRequested.TOPIC, workspace.getId().toString(), MemberJoinRequested.TYPE,
                new MemberJoinRequested(workspace.getId(), workspace.getName(), request.getId(), userId, adminUserId));
        return new JoinRequestResponse(request.getId(), userId, null, null, request.getStatus(), request.getRequestedAt());
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> pendingJoinRequests(Long workspaceId, Long adminUserId) {
        folderAccess.requireAdmin(workspaceId, adminUserId);
        List<JoinRequest> requests = joinRequestRepository
                .findByWorkspaceIdAndStatusOrderByRequestedAtAsc(workspaceId, JoinRequestStatus.PENDING);
        Map<Long, UserSummary> users = userClient.findUsers(requests.stream().map(JoinRequest::getUserId).toList());
        return requests.stream().map(request -> {
            UserSummary user = users.get(request.getUserId());
            return new JoinRequestResponse(request.getId(), request.getUserId(), user == null ? null : user.name(),
                    user == null ? null : user.email(), request.getStatus(), request.getRequestedAt());
        }).toList();
    }

    /** REQ-035. 승인하면 멤버가 되고 요청자에게 알림 이벤트를 보낸다. */
    @Transactional
    public void approve(Long workspaceId, Long joinRequestId, Long adminUserId) {
        JoinRequest request = decidablePendingRequest(workspaceId, joinRequestId, adminUserId);
        request.decide(JoinRequestStatus.APPROVED, adminUserId, now());
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, request.getUserId())) {
            workspaceMemberRepository.save(new WorkspaceMember(workspaceId, request.getUserId(), WorkspaceRole.MEMBER, now()));
        }
        Workspace workspace = workspaceOf(workspaceId);
        outboxEventWriter.append(MemberJoinApproved.TOPIC, workspaceId.toString(), MemberJoinApproved.TYPE,
                new MemberJoinApproved(workspaceId, workspace.getName(), request.getUserId()));
    }

    /** REQ-017. 요청자는 초대 화면에서 REJECTED 상태를 보고 다시 요청할 수 있다. */
    @Transactional
    public void reject(Long workspaceId, Long joinRequestId, Long adminUserId) {
        decidablePendingRequest(workspaceId, joinRequestId, adminUserId)
                .decide(JoinRequestStatus.REJECTED, adminUserId, now());
    }

    /** REQ-033. 관리자만 볼 수 있다. */
    @Transactional(readOnly = true)
    public List<MemberResponse> members(Long workspaceId, Long adminUserId) {
        folderAccess.requireAdmin(workspaceId, adminUserId);
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceIdOrderByJoinedAtAsc(workspaceId);
        Map<Long, UserSummary> users = userClient.findUsers(members.stream().map(WorkspaceMember::getUserId).toList());
        return members.stream().map(member -> {
            UserSummary user = users.get(member.getUserId());
            return new MemberResponse(member.getUserId(), user == null ? null : user.name(),
                    user == null ? null : user.email(), user == null ? null : user.profileImageUrl(),
                    member.getRole(), member.getJoinedAt());
        }).toList();
    }

    /** REQ-036 내보내기. 내보낸 사람이 가진 폴더는 자동으로 양도한다. */
    @Transactional
    public void removeMember(Long workspaceId, Long adminUserId, Long targetUserId) {
        folderAccess.requireAdmin(workspaceId, adminUserId);
        if (adminUserId.equals(targetUserId)) {
            throw new BusinessException(WorkspaceErrorCode.ADMIN_CANNOT_LEAVE);
        }
        WorkspaceMember target = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        transferOwnedFolders(workspaceId, targetUserId, adminUserId);
        leaveWorkspace(target);
    }

    /** REQ-036 관리자 양도. 관리자는 워크스페이스마다 1명이므로 넘기는 사람은 일반 멤버가 된다. */
    @Transactional
    public void transferAdmin(Long workspaceId, Long adminUserId, Long targetUserId) {
        WorkspaceMember admin = folderAccess.requireAdmin(workspaceId, adminUserId);
        if (adminUserId.equals(targetUserId)) {
            return;
        }
        WorkspaceMember target = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        admin.changeRole(WorkspaceRole.MEMBER);
        target.changeRole(WorkspaceRole.ADMIN);
    }

    /** REQ-032. 관리자는 양도한 뒤에, 폴더 소유자는 폴더를 양도한 뒤에 나갈 수 있다. */
    @Transactional
    public void leave(Long workspaceId, Long userId) {
        WorkspaceMember member = folderAccess.requireMember(workspaceId, userId);
        if (member.isAdmin()) {
            throw new BusinessException(WorkspaceErrorCode.ADMIN_CANNOT_LEAVE);
        }
        if (!folderRepository.findByWorkspaceIdAndOwnerUserId(workspaceId, userId).isEmpty()) {
            throw new BusinessException(WorkspaceErrorCode.FOLDER_OWNERSHIP_REMAINING);
        }
        leaveWorkspace(member);
    }

    /**
     * 양도 없이 빠지는 사람의 폴더: 가장 먼저 초대된 폴더 멤버에게, 멤버가 없으면 관리자에게 넘긴다.
     */
    private void transferOwnedFolders(Long workspaceId, Long leavingUserId, Long adminUserId) {
        for (Folder folder : folderRepository.findByWorkspaceIdAndOwnerUserId(workspaceId, leavingUserId)) {
            Optional<FolderMember> next = folderMemberRepository.findByFolderIdOrderByInvitedAtAscIdAsc(folder.getId())
                    .stream()
                    .filter(member -> !member.getUserId().equals(leavingUserId))
                    .findFirst();
            Long newOwner = next.map(FolderMember::getUserId).orElse(adminUserId);
            folderMemberRepository.findByFolderIdAndUserId(folder.getId(), newOwner).ifPresent(folderMemberRepository::delete);
            folder.transferOwner(newOwner);
        }
    }

    private void leaveWorkspace(WorkspaceMember member) {
        Long workspaceId = member.getWorkspaceId();
        Long userId = member.getUserId();
        List<Long> folderIds = folderRepository.findByWorkspaceId(workspaceId).stream().map(Folder::getId).toList();
        if (!folderIds.isEmpty()) {
            folderMemberRepository.deleteByFolderIdInAndUserId(folderIds, userId);
            folderVisitRepository.deleteByFolderIdInAndUserId(folderIds, userId);
        }
        lastWorkspaceRepository.findById(userId)
                .filter(last -> last.getWorkspaceId().equals(workspaceId))
                .ifPresent(lastWorkspaceRepository::delete);
        workspaceMemberRepository.delete(member);
    }

    private JoinRequest decidablePendingRequest(Long workspaceId, Long joinRequestId, Long adminUserId) {
        folderAccess.requireAdmin(workspaceId, adminUserId);
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

    private WorkspaceMember adminOf(Long workspaceId) {
        return workspaceMemberRepository.findByWorkspaceIdAndRole(workspaceId, WorkspaceRole.ADMIN)
                .orElseThrow(() -> new IllegalStateException("관리자가 없는 워크스페이스: " + workspaceId));
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
