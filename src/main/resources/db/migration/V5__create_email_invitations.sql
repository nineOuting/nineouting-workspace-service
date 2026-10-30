-- REQ-127 (2026-10-26 기획서): 소유자가 이메일로 멤버를 초대한다. 받은 사람은 링크로 승인 없이 바로 들어온다
CREATE TABLE email_invitations
(
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    workspace_id     BIGINT       NOT NULL,
    email            VARCHAR(255) NOT NULL,
    token            VARCHAR(64)  NOT NULL,
    invited_by       BIGINT       NOT NULL,
    expires_at       TIMESTAMP(6) NOT NULL,
    accepted_user_id BIGINT       NULL,
    accepted_at      TIMESTAMP(6) NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_invitations_token UNIQUE (token)
);
CREATE INDEX idx_email_invitations_workspace ON email_invitations (workspace_id, lower(email));
