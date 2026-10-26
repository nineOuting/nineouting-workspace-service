package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.Favorite;
import com.nineouting.backend.workspace.domain.FavoriteType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndTargetTypeAndTargetId(Long userId, FavoriteType targetType, Long targetId);

    List<Favorite> findByUserIdOrderByCreatedAtAsc(Long userId);
}
