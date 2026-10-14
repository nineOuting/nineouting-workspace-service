package com.nineouting.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FolderFlowTest extends ApiTestSupport {

    private Long admin;
    private Long owner;
    private Long member;
    private Long workspaceId;

    @BeforeEach
    void setUpWorkspace() throws Exception {
        admin = newUser();
        owner = newUser();
        member = newUser();
        workspaceId = createWorkspace(admin);
        join(workspaceId, admin, owner);
        join(workspaceId, admin, member);
    }

    @Test
    void 폴더_이름은_프로젝트_폴더끼리_하위_폴더는_같은_상위_안에서만_겹칠_수_없다() throws Exception {
        Long a = createFolder(workspaceId, owner, "기획", null);
        Long b = createFolder(workspaceId, owner, "개발", null);

        call(owner, post("/api/workspaces/" + workspaceId + "/folders"), "{\"name\":\"기획\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_DUPLICATED"));
        createFolder(workspaceId, owner, "회의", a);
        createFolder(workspaceId, owner, "회의", b);
        call(owner, post("/api/workspaces/" + workspaceId + "/folders"), "{\"name\":\"회의\",\"parentId\":%d}".formatted(a))
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_DUPLICATED"));
    }

    @Test
    void 폴더는_2단계까지만_만들_수_있다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        Long sub = createFolder(workspaceId, owner, "1차", project);

        call(owner, post("/api/workspaces/" + workspaceId + "/folders"), "{\"name\":\"더 아래\",\"parentId\":%d}".formatted(sub))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOLDER_DEPTH_EXCEEDED"));
    }

    @Test
    void 초대받지_않으면_관리자도_폴더에_들어갈_수_없다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(admin, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(admin, getRequest("/api/workspaces/" + workspaceId + "/folders"), null)
                .andExpect(jsonPath("$.data.length()").value(1));

        inviteToFolder(project, owner, member);
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isOk());
    }

    @Test
    void 기본_폴더는_멤버_누구나_들어가고_이름은_관리자가_바꾼다() throws Exception {
        Long defaultFolder = defaultFolder(workspaceId, member);

        call(member, getRequest("/api/folders/" + defaultFolder), null).andExpect(status().isOk());
        call(member, patchRequest("/api/folders/" + defaultFolder), "{\"name\":\"공용\"}").andExpect(status().isForbidden());
        call(admin, patchRequest("/api/folders/" + defaultFolder), "{\"name\":\"공용\"}")
                .andExpect(jsonPath("$.data.name").value("공용"));
        call(admin, deleteRequest("/api/folders/" + defaultFolder), null)
                .andExpect(jsonPath("$.code").value("DEFAULT_FOLDER_UNDELETABLE"));
    }

    @Test
    void 상위_폴더_멤버는_하위_폴더에_자동으로_들어가고_하위에만_초대된_사람은_상위에_못_들어간다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        Long sub = createFolder(workspaceId, owner, "1차", project);
        Long outsider = newUser();
        join(workspaceId, admin, outsider);
        inviteToFolder(project, owner, member);
        inviteToFolder(sub, owner, outsider);

        call(member, getRequest("/api/folders/" + sub), null).andExpect(status().isOk());
        call(outsider, getRequest("/api/folders/" + sub), null)
                .andExpect(jsonPath("$.data.path[0].accessible").value(false));
        call(outsider, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        // 목록에는 상위 폴더 이름만 보이고 들어갈 수는 없다
        call(outsider, getRequest("/api/workspaces/" + workspaceId + "/folders"), null)
                .andExpect(jsonPath("$.data[1].folderId").value(project))
                .andExpect(jsonPath("$.data[1].accessible").value(false))
                .andExpect(jsonPath("$.data[1].children[0].folderId").value(sub));

        call(owner, getRequest("/api/folders/" + sub + "/members"), null)
                .andExpect(jsonPath("$.data[?(@.userId == " + member + ")].inherited").value(true))
                .andExpect(jsonPath("$.data[?(@.userId == " + outsider + ")].inherited").value(false));
    }

    @Test
    void 초대와_내보내기는_소유자만_같은_워크스페이스_멤버에게만_할_수_있다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        call(member, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(admin))
                .andExpect(status().isForbidden());
        call(owner, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(newUser()))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        inviteToFolder(project, owner, member);
        call(owner, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(member))
                .andExpect(jsonPath("$.code").value("ALREADY_FOLDER_MEMBER"));

        call(owner, deleteRequest("/api/folders/" + project + "/members/" + member), null).andExpect(status().isOk());
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
    }

    @Test
    void 소유권은_폴더_멤버에게만_넘길_수_있고_넘긴_사람은_멤버로_남는다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        call(owner, putRequest("/api/folders/" + project + "/owner"), "{\"userId\":%d}".formatted(member))
                .andExpect(jsonPath("$.code").value("NOT_FOLDER_MEMBER"));
        inviteToFolder(project, owner, member);
        call(owner, putRequest("/api/folders/" + project + "/owner"), "{\"userId\":%d}".formatted(member))
                .andExpect(status().isOk());

        call(member, getRequest("/api/folders/" + project), null).andExpect(jsonPath("$.data.isOwner").value(true));
        call(owner, getRequest("/api/folders/" + project), null).andExpect(jsonPath("$.data.isOwner").value(false));
    }

    @Test
    void 폴더를_지우면_하위_폴더까지_지워지고_삭제_이벤트가_남는다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        Long sub = createFolder(workspaceId, owner, "1차", project);
        inviteToFolder(project, owner, member);

        call(member, deleteRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(owner, deleteRequest("/api/folders/" + project), null).andExpect(status().isOk());

        call(owner, getRequest("/api/folders/" + project), null).andExpect(status().isNotFound());
        call(owner, getRequest("/api/folders/" + sub), null).andExpect(status().isNotFound());
        assertThat(outboxContains("FolderDeleted", "\"folderIds\":[" + sub + "," + project + "]")).isTrue();
    }

    @Test
    void 최근_프로젝트는_지금도_들어갈_수_있는_폴더만_최근_순으로_보인다() throws Exception {
        Long first = createFolder(workspaceId, owner, "첫째", null);
        Long second = createFolder(workspaceId, owner, "둘째", null);
        inviteToFolder(first, owner, member);
        inviteToFolder(second, owner, member);
        call(member, getRequest("/api/folders/" + first), null);
        clock.advance(java.time.Duration.ofSeconds(1));
        call(member, getRequest("/api/folders/" + second), null);

        call(member, getRequest("/api/users/me/recent-folders"), null)
                .andExpect(jsonPath("$.data[0].folderId").value(second))
                .andExpect(jsonPath("$.data[1].folderId").value(first))
                .andExpect(jsonPath("$.data[0].workspaceName").value("팀 워크스페이스"));

        call(owner, deleteRequest("/api/folders/" + second + "/members/" + member), null);
        call(member, getRequest("/api/users/me/recent-folders"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].folderId").value(first));
    }

    @Test
    void 다른_서비스는_폴더_권한과_멤버를_조회한다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        inviteToFolder(project, owner, member);

        mockMvc.perform(getRequest("/internal/folders/" + project + "/access").param("userId", member.toString()))
                .andExpect(jsonPath("$.data.allowed").value(true));
        mockMvc.perform(getRequest("/internal/folders/" + project + "/access").param("userId", admin.toString()))
                .andExpect(jsonPath("$.data.allowed").value(false));
        mockMvc.perform(getRequest("/internal/folders/" + project + "/members"))
                .andExpect(jsonPath("$.data", containsInAnyOrder(owner.intValue(), member.intValue())));
        mockMvc.perform(getRequest("/internal/users/" + member + "/folders"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
