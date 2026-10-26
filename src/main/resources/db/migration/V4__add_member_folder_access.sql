-- REQ-128 (2026-10-26 기획서): 멤버마다 폴더 접근 권한. ALL = 모든 폴더, INVITED = 기본 폴더와 초대받은 폴더만 (기본값)
ALTER TABLE workspace_members ADD COLUMN folder_access VARCHAR(20) NOT NULL DEFAULT 'INVITED';
