package com.nineouting.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "last_workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LastWorkspace {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    public LastWorkspace(Long userId, Long workspaceId) {
        this.userId = userId;
        this.workspaceId = workspaceId;
    }

    public void change(Long workspaceId) {
        this.workspaceId = workspaceId;
    }
}
