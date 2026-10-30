package com.nineouting.backend.workspace.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.workspace.MemberInvitedByEmail;
import com.nineouting.backend.common.outbox.OutboxEventWriter;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.workspace.config.WorkspaceProperties;
import com.nineouting.backend.workspace.domain.EmailInvitation;
import com.nineouting.backend.workspace.domain.Workspace;
import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.domain.WorkspaceRole;
import com.nineouting.backend.workspace.dto.EmailInvitationInfoResponse;
import com.nineouting.backend.workspace.dto.EmailInvitationResponse;
import com.nineouting.backend.workspace.dto.WorkspaceResponse;
import com.nineouting.backend.workspace.error.WorkspaceErrorCode;
import com.nineouting.backend.workspace.repository.EmailInvitationRepository;
import com.nineouting.backend.workspace.repository.WorkspaceMemberRepository;
import com.nineouting.backend.workspace.repository.WorkspaceRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일로 멤버 초대 (REQ-127, 2026-10-26 기획서). 소유자만 보낸다.
 * 받은 사람은 링크를 열어 로그인(가입 전이면 가입)한 뒤 수락하면 승인 없이 바로 멤버가 된다. 초대받은 이메일 계정만 수락할 수 있다.
 * 초대 링크·참여 요청(REQ-015·034, 016·035 레거시)은 그대로 둔다. 유효 기간은 초대 링크와 같다.
 */
@Service
@RequiredArgsConstructor
public class EmailInvitationService {

    private static final int TOKEN_BYTES = 32;

    private final EmailInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final FolderAccess folderAccess;
    private final UserClient userClient;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkspaceProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 같은 이메일로 아직 쓰지 않은 초대가 있으면 지우고 새로 보낸다 */
    @Transactional
    public EmailInvitationResponse invite(Long workspaceId, Long ownerUserId, String email) {
        folderAccess.requireWorkspaceOwner(workspaceId, ownerUserId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        invitationRepository.deleteByWorkspaceIdAndEmailAndAcceptedAtIsNull(workspaceId, normalized);
        LocalDateTime now = now();
        EmailInvitation invitation = invitationRepository.save(new EmailInvitation(workspaceId, normalized, newToken(),
                ownerUserId, now.plus(properties.inviteLinkTtl()), now));
        outboxEventWriter.append(MemberInvitedByEmail.TOPIC, workspaceId.toString(), MemberInvitedByEmail.TYPE,
                new MemberInvitedByEmail(workspaceId, workspace.getName(), normalized, invitation.getToken(), ownerUserId,
                        invitation.getExpiresAt()));
        return new EmailInvitationResponse(invitation.getId(), normalized, invitation.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public EmailInvitationInfoResponse info(String token, Long userId) {
        EmailInvitation invitation = usable(token);
        Workspace workspace = workspaceOf(invitation);
        return new EmailInvitationInfoResponse(workspace.getId(), workspace.getName(), invitation.getEmail(),
                emailMatches(invitation, userId),
                workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), userId));
    }

    /** 초대받은 이메일 계정이면 바로 멤버가 된다. 이미 멤버면 초대만 쓴 것으로 남긴다 */
    @Transactional
    public WorkspaceResponse accept(String token, Long userId) {
        EmailInvitation invitation = usable(token);
        if (!emailMatches(invitation, userId)) {
            throw new BusinessException(WorkspaceErrorCode.INVITATION_EMAIL_MISMATCH);
        }
        Workspace workspace = workspaceOf(invitation);
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId)
                .orElseGet(() -> workspaceMemberRepository.save(
                        new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.MEMBER, now())));
        invitation.accept(userId, now());
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), member.getRole(), false);
    }

    private EmailInvitation usable(String token) {
        return invitationRepository.findByToken(token)
                .filter(invitation -> invitation.isUsable(now()))
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.INVITATION_INVALID));
    }

    private Workspace workspaceOf(EmailInvitation invitation) {
        return workspaceRepository.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.INVITATION_INVALID));
    }

    private boolean emailMatches(EmailInvitation invitation, Long userId) {
        UserSummary user = userClient.findUsers(Set.of(userId)).get(userId);
        return user != null && user.email() != null && invitation.getEmail().equalsIgnoreCase(user.email().trim());
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
