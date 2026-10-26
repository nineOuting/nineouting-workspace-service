-- REQ-016, 031~033, 129 (2026-10-26 기획서): 관리자(ADMIN)를 소유자(OWNER)로 부른다. 워크스페이스마다 정확히 1명
UPDATE workspace_members SET role = 'OWNER' WHERE role = 'ADMIN';
