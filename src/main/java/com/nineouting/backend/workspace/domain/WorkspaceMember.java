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
@Table(name = "workspace_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** REQ-128 */
    @Enumerated(EnumType.STRING)
    @Column(name = "folder_access", nullable = false, length = 20)
    private FolderAccessScope folderAccess;

    public WorkspaceMember(Long workspaceId, Long userId, WorkspaceRole role, LocalDateTime joinedAt) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.folderAccess = FolderAccessScope.INVITED;
    }

    public boolean isOwner() {
        return role == WorkspaceRole.OWNER;
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
    }

    public void changeFolderAccess(FolderAccessScope folderAccess) {
        this.folderAccess = folderAccess;
    }

    public boolean canAccessAllFolders() {
        return folderAccess == FolderAccessScope.ALL;
    }
}
