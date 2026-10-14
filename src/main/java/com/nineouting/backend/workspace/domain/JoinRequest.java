package com.nineouting.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "join_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public JoinRequest(Long workspaceId, Long userId, LocalDateTime requestedAt) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.status = JoinRequestStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    public void decide(JoinRequestStatus result, Long adminUserId, LocalDateTime now) {
        this.status = result;
        this.decidedBy = adminUserId;
        this.decidedAt = now;
    }
}
