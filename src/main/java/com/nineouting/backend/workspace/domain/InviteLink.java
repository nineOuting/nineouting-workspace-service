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

@Getter
@Entity
@Table(name = "invite_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, unique = true)
    private Long workspaceId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public InviteLink(Long workspaceId, String token, LocalDateTime expiresAt, Long createdBy) {
        this.workspaceId = workspaceId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    /** 새 링크를 발급하면 이전 토큰은 더 이상 쓸 수 없다. */
    public void reissue(String token, LocalDateTime expiresAt, Long createdBy) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
