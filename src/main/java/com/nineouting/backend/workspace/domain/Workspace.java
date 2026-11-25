package com.nineouting.backend.workspace.domain;

import com.nineouting.backend.common.web.entity.BaseTimeEntity;
import com.nineouting.backend.workspace.service.AccessibleFolderChangeListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
// 들어갈 수 있는 폴더 캐시를 버린다
@EntityListeners(AccessibleFolderChangeListener.class)
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** REQ-163 */
    @Column(nullable = false, length = 100)
    private String description = "";

    /** REQ-199 회의록 기본 양식 (minutes-service 양식 ID). null이면 서비스 기본 양식 */
    @Column(name = "default_template_id")
    private Long defaultTemplateId;

    public Workspace(String name) {
        this.name = name;
    }

    /** REQ-163 */
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** REQ-198 소유자가 멤버의 폴더 접근 권한을 한 번이라도 정했는지 (워크스페이스 시작하기) */
    @Column(name = "folder_access_configured", nullable = false)
    private boolean folderAccessConfigured;

    public void markFolderAccessConfigured() {
        this.folderAccessConfigured = true;
    }

    /** REQ-199 */
    public void changeDefaultTemplate(Long templateId) {
        this.defaultTemplateId = templateId;
    }
}
