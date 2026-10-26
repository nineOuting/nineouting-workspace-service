package com.nineouting.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FolderFlowTest extends ApiTestSupport {

    private Long workspaceOwner;
    private Long owner;
    private Long member;
    private Long workspaceId;

    @BeforeEach
    void setUpWorkspace() throws Exception {
        workspaceOwner = newUser();
        owner = newUser();
        member = newUser();
        workspaceId = createWorkspace(workspaceOwner);
        join(workspaceId, workspaceOwner, owner);
        join(workspaceId, workspaceOwner, member);
    }

    @Test
    void 폴더_이름은_워크스페이스_안에서_겹칠_수_없다() throws Exception {
        createFolder(workspaceId, owner, "기획", null);

        call(member, post("/api/workspaces/" + workspaceId + "/folders"), "{\"name\":\"기획\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_DUPLICATED"));
        // 대소문자만 다른 이름도 같은 이름으로 본다
        createFolder(workspaceId, owner, "Design", null);
        call(owner, post("/api/workspaces/" + workspaceId + "/folders"), "{\"name\":\"design\"}")
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_DUPLICATED"));
    }

    @Test
    void 워크스페이스_멤버_누구나_폴더를_만들고_폴더_안에는_폴더를_만들_수_없다() throws Exception {
        Long project = createFolder(workspaceId, member, "기획", null);

        call(member, post("/api/workspaces/" + workspaceId + "/folders"), "{\"name\":\"1차\",\"parentId\":%d}".formatted(project))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOLDER_DEPTH_EXCEEDED"));
    }

    @Test
    void 초대받지_않으면_소유자도_폴더에_들어갈_수_없다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(workspaceOwner, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(workspaceOwner, getRequest("/api/workspaces/" + workspaceId + "/folders"), null)
                .andExpect(jsonPath("$.data.length()").value(1));

        inviteToFolder(project, owner, member);
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isOk());
    }

    @Test
    void 기본_폴더는_멤버_누구나_들어가고_이름은_소유자가_바꾼다() throws Exception {
        Long defaultFolder = defaultFolder(workspaceId, member);

        call(member, getRequest("/api/folders/" + defaultFolder), null).andExpect(status().isOk());
        call(member, patchRequest("/api/folders/" + defaultFolder), "{\"name\":\"공용\"}").andExpect(status().isForbidden());
        call(workspaceOwner, patchRequest("/api/folders/" + defaultFolder), "{\"name\":\"공용\"}")
                .andExpect(jsonPath("$.data.name").value("공용"));
        call(workspaceOwner, deleteRequest("/api/folders/" + defaultFolder), null)
                .andExpect(jsonPath("$.code").value("DEFAULT_FOLDER_UNDELETABLE"));
    }


    @Test
    void 초대와_내보내기는_소유자만_같은_워크스페이스_멤버에게만_할_수_있다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        call(member, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(workspaceOwner))
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
    void 폴더를_지우면_삭제_이벤트가_남는다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        inviteToFolder(project, owner, member);

        call(member, deleteRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(owner, deleteRequest("/api/folders/" + project), null).andExpect(status().isOk());

        call(owner, getRequest("/api/folders/" + project), null).andExpect(status().isNotFound());
        assertThat(outboxContains("FolderDeleted", "\"folderIds\":[" + project + "]")).isTrue();
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
        mockMvc.perform(getRequest("/internal/folders/" + project + "/access").param("userId", workspaceOwner.toString()))
                .andExpect(jsonPath("$.data.allowed").value(false));
        mockMvc.perform(getRequest("/internal/folders/" + project + "/members"))
                .andExpect(jsonPath("$.data", containsInAnyOrder(owner.intValue(), member.intValue())));
        mockMvc.perform(getRequest("/internal/users/" + member + "/folders"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].workspaceName").value("팀 워크스페이스"));
    }
}
