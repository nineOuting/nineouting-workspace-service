package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.WorkspaceMember;
import com.nineouting.backend.workspace.domain.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    List<WorkspaceMember> findByWorkspaceIdOrderByJoinedAtAsc(Long workspaceId);

    List<WorkspaceMember> findByUserId(Long userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);

    long countByWorkspaceId(Long workspaceId);

    void deleteByWorkspaceId(Long workspaceId);
}
