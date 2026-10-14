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

/** 폴더에 초대된 멤버. 폴더를 만든 사람(소유자)은 여기 없다. */
@Getter
@Entity
@Table(name = "folder_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FolderMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "invited_at", nullable = false)
    private LocalDateTime invitedAt;

    public FolderMember(Long folderId, Long userId, LocalDateTime invitedAt) {
        this.folderId = folderId;
        this.userId = userId;
        this.invitedAt = invitedAt;
    }
}
