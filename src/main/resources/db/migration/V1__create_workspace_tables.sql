CREATE TABLE workspaces
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 관리자(ADMIN)는 워크스페이스마다 정확히 1명 (서비스에서 보장)
CREATE TABLE workspace_members
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    role         VARCHAR(20) NOT NULL,
    joined_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_workspace_members UNIQUE (workspace_id, user_id),
    INDEX idx_workspace_members_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 워크스페이스마다 링크 1개. 새로 발급하면 토큰과 만료 시각을 바꾼다
CREATE TABLE invite_links
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT      NOT NULL,
    token        VARCHAR(64) NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    created_by   BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_invite_links_workspace UNIQUE (workspace_id),
    CONSTRAINT uk_invite_links_token UNIQUE (token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE join_requests
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_by   BIGINT      NULL,
    decided_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_join_requests_workspace_status (workspace_id, status),
    INDEX idx_join_requests_user (workspace_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 2단계까지 (프로젝트 폴더 → 하위 폴더).
-- 이름 중복: 프로젝트 폴더(parent 없음)끼리는 워크스페이스 안에서, 하위 폴더는 같은 상위 폴더 안에서 불가.
-- parent_key는 이 규칙을 DB 유일 제약으로 걸기 위한 계산 컬럼이다 (NULL은 유일 제약에서 서로 다른 값으로 취급되기 때문).
CREATE TABLE folders
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    workspace_id  BIGINT      NOT NULL,
    parent_id     BIGINT      NULL,
    parent_key    BIGINT AS (IFNULL(parent_id, 0)) STORED,
    name          VARCHAR(50) NOT NULL,
    owner_user_id BIGINT      NULL,
    is_default    BOOLEAN     NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_folders_name UNIQUE (workspace_id, parent_key, name),
    INDEX idx_folders_parent (parent_id),
    INDEX idx_folders_owner (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 폴더를 만든 사람(owner_user_id)이 아닌, 초대된 멤버
CREATE TABLE folder_members
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    folder_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    invited_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_folder_members UNIQUE (folder_id, user_id),
    INDEX idx_folder_members_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 최근 접속한 프로젝트 폴더 (REQ-024)
CREATE TABLE folder_visits
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    folder_id  BIGINT      NOT NULL,
    visited_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_folder_visits UNIQUE (user_id, folder_id),
    INDEX idx_folder_visits_recent (user_id, visited_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 마지막으로 접속한 워크스페이스 (REQ-030)
CREATE TABLE last_workspaces
(
    user_id      BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
