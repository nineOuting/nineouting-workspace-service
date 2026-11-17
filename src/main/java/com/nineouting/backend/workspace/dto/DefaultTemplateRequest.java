package com.nineouting.backend.workspace.dto;

/**
 * REQ-199 워크스페이스 회의록 기본 양식.
 *
 * @param templateId minutes-service 양식 ID. null이면 서비스 기본 양식으로 되돌린다
 */
public record DefaultTemplateRequest(Long templateId) {
}
