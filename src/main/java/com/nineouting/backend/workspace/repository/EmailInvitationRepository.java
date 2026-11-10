package com.nineouting.backend.workspace.repository;

import com.nineouting.backend.workspace.domain.EmailInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailInvitationRepository extends JpaRepository<EmailInvitation, Long> {

    Optional<EmailInvitation> findByToken(String token);

    /** REQ-164 아직 수락하지 않은 초대 (최근 순) */
    List<EmailInvitation> findByWorkspaceIdAndAcceptedAtIsNullOrderByCreatedAtDesc(Long workspaceId);

    Optional<EmailInvitation> findByIdAndWorkspaceIdAndAcceptedAtIsNull(Long id, Long workspaceId);

    /** 같은 이메일로 아직 쓰지 않은 초대 (다시 초대하면 지운다) */
    void deleteByWorkspaceIdAndEmailAndAcceptedAtIsNull(Long workspaceId, String email);

    void deleteByWorkspaceId(Long workspaceId);
}
