package com.nineouting.backend.workspace.domain;

/** 멤버별 폴더 접근 권한 (REQ-128). 워크스페이스 소유자만 바꾼다 */
public enum FolderAccessScope {
    /** 워크스페이스의 모든 폴더에 들어간다 */
    ALL,
    /** 기본 폴더와 초대받은 폴더만 (새 멤버의 기본값) */
    INVITED
}
