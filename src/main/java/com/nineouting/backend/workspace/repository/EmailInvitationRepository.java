package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.EmailInvitation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailInvitationRepository extends JpaRepository<EmailInvitation, Long> {

    Optional<EmailInvitation> findByToken(String token);

    /** 같은 이메일로 아직 쓰지 않은 초대 (다시 초대하면 지운다) */
    void deleteByWorkspaceIdAndEmailAndAcceptedAtIsNull(Long workspaceId, String email);

    void deleteByWorkspaceId(Long workspaceId);
}
