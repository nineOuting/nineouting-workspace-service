-- REQ-143 (2026-10-28 기획서): 쓰레기통. 폴더를 지우면 바로 지우지 않고 3개월 보관한 뒤 영구 삭제한다.
-- 휴지통에 있는 동안은 어디에도 보이지 않고, 안의 회의·회의록도 폴더 권한 확인에서 막힌다.
ALTER TABLE folders ADD COLUMN deleted_at TIMESTAMP(6) NULL;
ALTER TABLE folders ADD COLUMN deleted_by_user_id BIGINT NULL;

-- 휴지통에 있는 폴더와 같은 이름의 새 폴더는 만들 수 있다 (복구할 때 이름이 겹치면 막는다)
DROP INDEX uk_folders_name;
CREATE UNIQUE INDEX uk_folders_name ON folders (workspace_id, COALESCE(parent_id, 0), lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_folders_deleted_at ON folders (deleted_at) WHERE deleted_at IS NOT NULL;
