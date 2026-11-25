package com.nineouting.backend.workspace.service;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 들어갈 수 있는 폴더에 영향을 주는 엔티티가 바뀌면 캐시를 버린다 ({@link AccessibleFolderCache}).
 * JPQL, 네이티브 일괄 수정은 여기로 오지 않으므로 부르는 곳에서 {@link AccessibleFolderCache#changed()}를 직접 부른다.
 */
@Component
@RequiredArgsConstructor
public class AccessibleFolderChangeListener {

    private final AccessibleFolderCache accessibleFolderCache;

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(Object entity) {
        accessibleFolderCache.changed();
    }
}
