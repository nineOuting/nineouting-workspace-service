package com.nineouting.backend.workspace.domain;

import com.nineouting.backend.common.web.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 폴더. 폴더 안에 폴더는 만들 수 없다 (REQ-040, 2026-10-26). parentId는 예전 하위 폴더 구조로, 새로 만들 때는 늘 null
 * 기본 폴더는 워크스페이스마다 1개 자동으로 생기고, 소유자가 없으며 지울 수 없다 (REQ-044).
 */
@Getter
@Entity
@Table(name = "folders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Folder extends BaseTimeEntity {

    public static final String DEFAULT_NAME = "기본 폴더";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "is_default", nullable = false)
    private boolean defaultFolder;

    private Folder(Long workspaceId, Long parentId, String name, Long ownerUserId, boolean defaultFolder) {
        this.workspaceId = workspaceId;
        this.parentId = parentId;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.defaultFolder = defaultFolder;
    }

    public static Folder createDefault(Long workspaceId) {
        return new Folder(workspaceId, null, DEFAULT_NAME, null, true);
    }

    public static Folder create(Long workspaceId, Long parentId, String name, Long ownerUserId) {
        return new Folder(workspaceId, parentId, name, ownerUserId, false);
    }

    public boolean isTopLevel() {
        return parentId == null;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerUserId != null && ownerUserId.equals(userId);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void transferOwner(Long newOwnerUserId) {
        this.ownerUserId = newOwnerUserId;
    }
}
