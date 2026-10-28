package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.Favorite;
import com.nineouting.backend.workspace.domain.FavoriteType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndTargetTypeAndTargetId(Long userId, FavoriteType targetType, Long targetId);

    List<Favorite> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** 영구 삭제한 폴더의 즐겨찾기 (REQ-143) */
    void deleteByTargetTypeAndTargetIdIn(FavoriteType targetType, Collection<Long> targetIds);
}
