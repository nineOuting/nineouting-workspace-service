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
@Table(name = "folder_visits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FolderVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(name = "visited_at", nullable = false)
    private LocalDateTime visitedAt;

    public FolderVisit(Long userId, Long folderId, LocalDateTime visitedAt) {
        this.userId = userId;
        this.folderId = folderId;
        this.visitedAt = visitedAt;
    }

    public void touch(LocalDateTime now) {
        this.visitedAt = now;
    }
}
