-- REQ-163 워크스페이스 설정 창에서 고치는 설명 (없으면 빈 글자)
ALTER TABLE workspaces ADD COLUMN description VARCHAR(100) NOT NULL DEFAULT '';
