package com.nineouting.backend.workspace.error;

import com.nineouting.backend.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * workspace-service 에러 코드 (docs/services/workspace-service.md 4.6).
 * user-service 연결 실패(503)는 client-common의 ClientErrorCode를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum WorkspaceErrorCode implements ErrorCode {

    NOT_WORKSPACE_MEMBER(HttpStatus.FORBIDDEN, "워크스페이스 멤버가 아닙니다"),
    INVITE_LINK_INVALID(HttpStatus.NOT_FOUND, "초대 링크가 만료되었거나 올바르지 않습니다"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 참여 중인 워크스페이스입니다"),
    JOIN_REQUEST_PENDING(HttpStatus.CONFLICT, "이미 참여를 요청했습니다. 승인을 기다려 주세요"),
    JOIN_REQUEST_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리된 참여 요청입니다"),
    FOLDER_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 폴더가 있습니다"),
    FOLDER_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "폴더는 2단계까지만 만들 수 있습니다"),
    DEFAULT_FOLDER_UNDELETABLE(HttpStatus.BAD_REQUEST, "기본 폴더는 삭제할 수 없습니다"),
    OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "소유권을 양도해야 워크스페이스에서 나갈 수 있습니다. 다른 멤버에게 소유권을 양도한 후 다시 시도해 주세요"),
    FOLDER_OWNERSHIP_REMAINING(HttpStatus.BAD_REQUEST, "소유한 폴더를 다른 멤버에게 양도한 뒤에 나갈 수 있습니다"),
    NOT_FOLDER_MEMBER(HttpStatus.BAD_REQUEST, "폴더 멤버가 아닙니다"),
    ALREADY_FOLDER_MEMBER(HttpStatus.CONFLICT, "이미 폴더 멤버입니다");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
