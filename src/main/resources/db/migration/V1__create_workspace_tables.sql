CREATE TABLE workspaces
(
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

-- 관리자(ADMIN)는 워크스페이스마다 정확히 1명 (서비스에서 보장)
CREATE TABLE workspace_members
(
    id           BIGINT      GENERATED ALWAYS AS IDENTITY,
    workspace_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    role         VARCHAR(20) NOT NULL,
    joined_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_workspace_members UNIQUE (workspace_id, user_id)
);
CREATE INDEX idx_workspace_members_user ON workspace_members (user_id);

-- 워크스페이스마다 링크 1개. 새로 발급하면 토큰과 만료 시각을 바꾼다
CREATE TABLE invite_links
(
    id           BIGINT      GENERATED ALWAYS AS IDENTITY,
    workspace_id BIGINT      NOT NULL,
    token        VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMP(6) NOT NULL,
    created_by   BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_invite_links_workspace UNIQUE (workspace_id),
    CONSTRAINT uk_invite_links_token UNIQUE (token)
);

CREATE TABLE join_requests
(
    id           BIGINT      GENERATED ALWAYS AS IDENTITY,
    workspace_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    decided_by   BIGINT      NULL,
    decided_at   TIMESTAMP(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_join_requests_workspace_status ON join_requests (workspace_id, status);
CREATE INDEX idx_join_requests_user ON join_requests (workspace_id, user_id);

-- 2단계까지 (프로젝트 폴더 → 하위 폴더).
-- 이름 중복: 프로젝트 폴더(parent 없음)끼리는 워크스페이스 안에서, 하위 폴더는 같은 상위 폴더 안에서 불가.
-- 유일 인덱스에서 NULL은 서로 다른 값으로 취급되므로 parent_id가 없으면 0으로 바꿔 비교하고, 대소문자는 구분하지 않는다.
CREATE TABLE folders
(
    id            BIGINT      GENERATED ALWAYS AS IDENTITY,
    workspace_id  BIGINT      NOT NULL,
    parent_id     BIGINT      NULL,
    name          VARCHAR(50) NOT NULL,
    owner_user_id BIGINT      NULL,
    is_default    BOOLEAN     NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_folders_name ON folders (workspace_id, COALESCE(parent_id, 0), lower(name));
CREATE INDEX idx_folders_parent ON folders (parent_id);
CREATE INDEX idx_folders_owner ON folders (owner_user_id);

-- 폴더를 만든 사람(owner_user_id)이 아닌, 초대된 멤버
CREATE TABLE folder_members
(
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    folder_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    invited_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_folder_members UNIQUE (folder_id, user_id)
);
CREATE INDEX idx_folder_members_user ON folder_members (user_id);

-- 최근 접속한 프로젝트 폴더 (REQ-024)
CREATE TABLE folder_visits
(
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    user_id    BIGINT      NOT NULL,
    folder_id  BIGINT      NOT NULL,
    visited_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_folder_visits UNIQUE (user_id, folder_id)
);
CREATE INDEX idx_folder_visits_recent ON folder_visits (user_id, visited_at);

-- 마지막으로 접속한 워크스페이스 (REQ-030)
CREATE TABLE last_workspaces
(
    user_id      BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    PRIMARY KEY (user_id)
);
