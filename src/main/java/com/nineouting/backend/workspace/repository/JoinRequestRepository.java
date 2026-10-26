package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.JoinRequest;
import com.nineouting.backend.workspace.domain.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    boolean existsByWorkspaceIdAndUserIdAndStatus(Long workspaceId, Long userId, JoinRequestStatus status);

    Optional<JoinRequest> findFirstByWorkspaceIdAndUserIdOrderByIdDesc(Long workspaceId, Long userId);

    List<JoinRequest> findByWorkspaceIdAndStatusOrderByRequestedAtAsc(Long workspaceId, JoinRequestStatus status);

    void deleteByWorkspaceId(Long workspaceId);
}
