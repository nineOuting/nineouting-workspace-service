package com.nineouting.backend.workspace.domain;

import com.nineouting.backend.common.web.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
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

    /** REQ-199 */
    public void changeDefaultTemplate(Long templateId) {
        this.defaultTemplateId = templateId;
    }
}
