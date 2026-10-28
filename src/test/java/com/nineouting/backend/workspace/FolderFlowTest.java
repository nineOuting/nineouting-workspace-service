package com.nineouting.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.workspace.service.TrashService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FolderFlowTest extends ApiTestSupport {

    @Autowired
    TrashService trashService;

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

        inviteToFolder(project, workspaceOwner, member);
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isOk());
    }

    @Test
    void 폴더_접근_권한을_모든_폴더로_바꾸면_초대받지_않은_폴더에도_들어간다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        String path = "/api/workspaces/" + workspaceId + "/members/" + member + "/folder-access";

        call(workspaceOwner, getRequest("/api/workspaces/" + workspaceId + "/members"), null)
                .andExpect(jsonPath("$.data[?(@.userId == " + member + ")].folderAccess").value("INVITED"));
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(owner, putRequest(path), "{\"folderAccess\":\"ALL\"}").andExpect(status().isForbidden());

        call(workspaceOwner, putRequest(path), "{\"folderAccess\":\"ALL\"}").andExpect(status().isOk());
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isOk());
        call(owner, getRequest("/api/folders/" + project + "/members"), null)
                .andExpect(jsonPath("$.data[?(@.userId == " + member + ")].inherited").value(true));
        mockMvc.perform(getRequest("/internal/folders/" + project + "/access").param("userId", member.toString()))
                .andExpect(jsonPath("$.data.allowed").value(true));

        call(workspaceOwner, putRequest(path), "{\"folderAccess\":\"INVITED\"}").andExpect(status().isOk());
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(workspaceOwner, putRequest(path), "{\"folderAccess\":\"NONE\"}").andExpect(status().isBadRequest());
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
    void 초대와_내보내기는_워크스페이스_소유자만_같은_워크스페이스_멤버에게만_할_수_있다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        // 폴더 소유자라도 워크스페이스 소유자가 아니면 초대·내보내기를 할 수 없다 (2026-10-26 결정)
        call(owner, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(member))
                .andExpect(status().isForbidden());
        call(member, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(workspaceOwner))
                .andExpect(status().isForbidden());
        call(workspaceOwner, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(newUser()))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        inviteToFolder(project, workspaceOwner, member);
        call(workspaceOwner, post("/api/folders/" + project + "/members"), "{\"userId\":%d}".formatted(member))
                .andExpect(jsonPath("$.code").value("ALREADY_FOLDER_MEMBER"));

        call(owner, deleteRequest("/api/folders/" + project + "/members/" + member), null)
                .andExpect(status().isForbidden());
        call(workspaceOwner, deleteRequest("/api/folders/" + project + "/members/" + member), null)
                .andExpect(status().isOk());
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
    }

    @Test
    void 초대받은_멤버는_폴더에서_나갈_수_있고_소유자는_양도해야_나간다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        inviteToFolder(project, workspaceOwner, member);

        call(owner, deleteRequest("/api/folders/" + project + "/members/me"), null)
                .andExpect(jsonPath("$.code").value("FOLDER_OWNER_CANNOT_LEAVE"));
        call(member, deleteRequest("/api/folders/" + project + "/members/me"), null).andExpect(status().isOk());
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isForbidden());

        call(member, deleteRequest("/api/folders/" + defaultFolder(workspaceId, member) + "/members/me"), null)
                .andExpect(jsonPath("$.code").value("DEFAULT_FOLDER_UNLEAVABLE"));
        call(workspaceOwner, putRequest("/api/workspaces/" + workspaceId + "/members/" + member + "/folder-access"),
                "{\"folderAccess\":\"ALL\"}");
        call(member, deleteRequest("/api/folders/" + project + "/members/me"), null)
                .andExpect(jsonPath("$.code").value("NOT_FOLDER_MEMBER"));
    }

    @Test
    void 소유권은_폴더_멤버에게만_넘길_수_있고_넘긴_사람은_멤버로_남는다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);

        call(owner, putRequest("/api/folders/" + project + "/owner"), "{\"userId\":%d}".formatted(member))
                .andExpect(jsonPath("$.code").value("NOT_FOLDER_MEMBER"));
        inviteToFolder(project, workspaceOwner, member);
        call(owner, putRequest("/api/folders/" + project + "/owner"), "{\"userId\":%d}".formatted(member))
                .andExpect(status().isOk());

        call(member, getRequest("/api/folders/" + project), null).andExpect(jsonPath("$.data.isOwner").value(true));
        call(owner, getRequest("/api/folders/" + project), null).andExpect(jsonPath("$.data.isOwner").value(false));
    }

    @Test
    void 폴더를_지우면_쓰레기통으로_가서_안_보이고_복구하면_멤버와_함께_돌아온다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        inviteToFolder(project, workspaceOwner, member);

        call(member, deleteRequest("/api/folders/" + project), null).andExpect(status().isForbidden());
        call(owner, deleteRequest("/api/folders/" + project), null).andExpect(status().isOk());

        call(owner, getRequest("/api/folders/" + project), null).andExpect(status().isNotFound());
        call(member, getRequest("/api/workspaces/" + workspaceId + "/folders"), null)
                .andExpect(jsonPath("$.data[*].name").value(not(hasItem("기획"))));
        // 영구 삭제 전에는 안의 회의·회의록을 지우지 않는다
        assertThat(outboxContains("FolderDeleted", "\"folderIds\":[" + project + "]")).isFalse();

        // 지운 사람과 워크스페이스 소유자만 쓰레기통에서 본다
        call(owner, getRequest("/api/workspaces/" + workspaceId + "/trash"), null)
                .andExpect(jsonPath("$.data[0].folderId").value(project))
                .andExpect(jsonPath("$.data[0].deletedByUserId").value(owner))
                .andExpect(jsonPath("$.data[0].deletedByName").value("user" + owner))
                .andExpect(jsonPath("$.data[0].purgeAt").isNotEmpty());
        call(workspaceOwner, getRequest("/api/workspaces/" + workspaceId + "/trash"), null)
                .andExpect(jsonPath("$.data.length()").value(1));
        call(member, getRequest("/api/workspaces/" + workspaceId + "/trash"), null)
                .andExpect(jsonPath("$.data.length()").value(0));
        call(member, post("/api/workspaces/" + workspaceId + "/trash/folders/" + project + "/restore"), null)
                .andExpect(jsonPath("$.code").value("TRASH_FORBIDDEN"));

        call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/trash/folders/" + project + "/restore"), null)
                .andExpect(status().isOk());
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isOk());
        call(owner, getRequest("/api/workspaces/" + workspaceId + "/trash"), null)
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 쓰레기통에_있는_폴더와_같은_이름은_새로_만들_수_있고_그러면_복구는_막힌다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        call(owner, deleteRequest("/api/folders/" + project), null).andExpect(status().isOk());

        createFolder(workspaceId, member, "기획", null);

        call(owner, post("/api/workspaces/" + workspaceId + "/trash/folders/" + project + "/restore"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_DUPLICATED"));
    }

    @Test
    void 영구_삭제하거나_보관_기간이_지나면_회의_회의록을_지우는_이벤트가_나간다() throws Exception {
        Long first = createFolder(workspaceId, owner, "첫째", null);
        Long second = createFolder(workspaceId, owner, "둘째", null);
        call(owner, deleteRequest("/api/folders/" + first), null).andExpect(status().isOk());
        call(owner, deleteRequest("/api/folders/" + second), null).andExpect(status().isOk());

        call(owner, deleteRequest("/api/workspaces/" + workspaceId + "/trash/folders/" + first), null)
                .andExpect(status().isOk());
        assertThat(outboxContains("FolderDeleted", "\"folderIds\":[" + first + "]")).isTrue();
        call(owner, post("/api/workspaces/" + workspaceId + "/trash/folders/" + first + "/restore"), null)
                .andExpect(status().isNotFound());

        trashService.purgeExpired();
        assertThat(outboxContains("FolderDeleted", "\"folderIds\":[" + second + "]")).isFalse();
        clock.advance(Duration.ofDays(91));
        trashService.purgeExpired();
        assertThat(outboxContains("FolderDeleted", "\"folderIds\":[" + second + "]")).isTrue();
        call(owner, getRequest("/api/workspaces/" + workspaceId + "/trash"), null)
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 최근_프로젝트는_지금도_들어갈_수_있는_폴더만_최근_순으로_보인다() throws Exception {
        Long first = createFolder(workspaceId, owner, "첫째", null);
        Long second = createFolder(workspaceId, owner, "둘째", null);
        inviteToFolder(first, workspaceOwner, member);
        inviteToFolder(second, workspaceOwner, member);
        call(member, getRequest("/api/folders/" + first), null);
        clock.advance(java.time.Duration.ofSeconds(1));
        call(member, getRequest("/api/folders/" + second), null);

        call(member, getRequest("/api/users/me/recent-folders"), null)
                .andExpect(jsonPath("$.data[0].folderId").value(second))
                .andExpect(jsonPath("$.data[1].folderId").value(first))
                .andExpect(jsonPath("$.data[0].workspaceName").value("팀 워크스페이스"));

        call(workspaceOwner, deleteRequest("/api/folders/" + second + "/members/" + member), null);
        call(member, getRequest("/api/users/me/recent-folders"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].folderId").value(first));
    }

    @Test
    void 다른_서비스는_폴더_권한과_멤버를_조회한다() throws Exception {
        Long project = createFolder(workspaceId, owner, "기획", null);
        inviteToFolder(project, workspaceOwner, member);

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
