package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
}
