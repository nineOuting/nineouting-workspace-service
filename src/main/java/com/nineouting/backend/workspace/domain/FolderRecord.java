package com.nineouting.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * folders 테이블을 쓰레기통 폴더까지 그대로 읽는 보기 (REQ-143). {@link Folder}는 쓰레기통 폴더를 빼고 보므로,
 * 쓰레기통 목록·복구·영구 삭제·워크스페이스 삭제는 이것으로 한다. 값은 바꾸지 않고 벌크 쿼리로만 고친다.
 */
@Getter
@Entity
@Table(name = "folders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FolderRecord {

    @Id
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_user_id")
    private Long deletedByUserId;

    public boolean isTrashed() {
        return deletedAt != null;
    }
}
