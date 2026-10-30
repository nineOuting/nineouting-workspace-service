package com.nineouting.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일 초대 (REQ-127). 토큰이 담긴 링크를 받은 그 이메일 계정만 승인 없이 들어온다.
 * 같은 이메일을 다시 초대하면 이전 초대는 무효가 된다 (서비스에서 지운다).
 */
@Getter
@Entity
@Table(name = "email_invitations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** 소문자로 저장한다 */
    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_user_id")
    private Long acceptedUserId;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public EmailInvitation(Long workspaceId, String email, String token, Long invitedBy, LocalDateTime expiresAt,
                           LocalDateTime createdAt) {
        this.workspaceId = workspaceId;
        this.email = email;
        this.token = token;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    /** 만료됐거나 이미 쓴 초대는 쓸 수 없다 */
    public boolean isUsable(LocalDateTime now) {
        return acceptedAt == null && now.isBefore(expiresAt);
    }

    public void accept(Long userId, LocalDateTime now) {
        this.acceptedUserId = userId;
        this.acceptedAt = now;
    }
}
