-- REQ-202 프로젝트 색 (8가지 중 하나, 0~7). 이미 있는 폴더는 번호대로 돌려 가며 칠한다
ALTER TABLE folders ADD COLUMN color INTEGER NOT NULL DEFAULT 0;
UPDATE folders SET color = id % 8;
-- REQ-199 워크스페이스 회의록 기본 양식. 비어 있으면 서비스 기본 양식
ALTER TABLE workspaces ADD COLUMN default_template_id BIGINT;
