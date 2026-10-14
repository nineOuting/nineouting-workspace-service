package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.InviteLink;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {

    Optional<InviteLink> findByWorkspaceId(Long workspaceId);

    Optional<InviteLink> findByToken(String token);
}
