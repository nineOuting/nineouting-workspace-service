package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.LastWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LastWorkspaceRepository extends JpaRepository<LastWorkspace, Long> {
}
