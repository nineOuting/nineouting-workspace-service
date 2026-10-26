package com.nineouting.backend.workspace;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FavoriteFlowTest extends ApiTestSupport {

    private static final String FAVORITES = "/api/users/me/favorites";

    private Long owner;
    private Long member;
    private Long workspaceId;

    @BeforeEach
    void setUpWorkspace() throws Exception {
        owner = newUser();
        member = newUser();
        workspaceId = createWorkspace(owner);
        join(workspaceId, owner, member);
    }

    @Test
    void 워크스페이스와_폴더를_즐겨찾기하면_등록한_순서대로_보인다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        call(owner, putRequest("/api/folders/" + project + "/favorite"), null).andExpect(status().isOk());
        call(owner, putRequest("/api/workspaces/" + workspaceId + "/favorite"), null).andExpect(status().isOk());
        // 두 번 등록해도 하나
        call(owner, putRequest("/api/folders/" + project + "/favorite"), null).andExpect(status().isOk());

        call(owner, getRequest(FAVORITES), null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("FOLDER"))
                .andExpect(jsonPath("$.data[0].folderId").value(project))
                .andExpect(jsonPath("$.data[0].folderName").value("기획"))
                .andExpect(jsonPath("$.data[0].workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.data[1].type").value("WORKSPACE"))
                .andExpect(jsonPath("$.data[1].folderId").doesNotExist());
        // 개인 표시라 다른 멤버에게는 보이지 않는다
        call(member, getRequest(FAVORITES), null).andExpect(jsonPath("$.data.length()").value(0));

        call(owner, deleteRequest("/api/folders/" + project + "/favorite"), null).andExpect(status().isOk());
        call(owner, deleteRequest("/api/folders/" + project + "/favorite"), null).andExpect(status().isOk());
        call(owner, getRequest(FAVORITES), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("WORKSPACE"));
    }

    @Test
    void 들어갈_수_없는_대상은_등록할_수_없고_지우거나_나가면_목록에서_빠진다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        call(member, putRequest("/api/folders/" + project + "/favorite"), null).andExpect(status().isForbidden());
        call(newUser(), putRequest("/api/workspaces/" + workspaceId + "/favorite"), null)
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));

        Long shared = createFolder(workspaceId, member, "공유", null);
        call(member, putRequest("/api/folders/" + shared + "/favorite"), null).andExpect(status().isOk());
        call(member, putRequest("/api/workspaces/" + workspaceId + "/favorite"), null).andExpect(status().isOk());
        call(member, deleteRequest("/api/folders/" + shared), null).andExpect(status().isOk());
        call(member, getRequest(FAVORITES), null).andExpect(jsonPath("$.data.length()").value(1));

        call(member, deleteRequest("/api/workspaces/" + workspaceId + "/members/me"), null).andExpect(status().isOk());
        call(member, getRequest(FAVORITES), null).andExpect(jsonPath("$.data.length()").value(0));
    }
}
