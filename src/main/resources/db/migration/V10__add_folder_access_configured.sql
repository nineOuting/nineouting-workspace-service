-- REQ-198 워크스페이스 시작하기의 "폴더 권한 정하기": 소유자가 멤버의 폴더 접근 권한을 한 번이라도 정했는지
ALTER TABLE workspaces ADD COLUMN folder_access_configured BOOLEAN NOT NULL DEFAULT FALSE;
