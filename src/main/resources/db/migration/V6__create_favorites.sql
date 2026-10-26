-- REQ-143~144 (2026-10-26 홈 디자인): 워크스페이스·폴더 즐겨찾기. 개인 표시라 본인 홈에만 보인다
CREATE TABLE favorites
(
    id          BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT       NOT NULL,
    target_type VARCHAR(20)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_favorites UNIQUE (user_id, target_type, target_id)
);
CREATE INDEX idx_favorites_user ON favorites (user_id, created_at);
