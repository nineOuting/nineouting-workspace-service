package com.nineouting.backend.workspace.dto;

import com.nineouting.backend.workspace.domain.FolderAccessScope;
import jakarta.validation.constraints.NotNull;

/** REQ-128 */
public record FolderAccessRequest(@NotNull(message = "폴더 접근 권한을 골라 주세요") FolderAccessScope folderAccess) {
}
