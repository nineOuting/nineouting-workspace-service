package com.nineouting.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MembershipFlowTest extends ApiTestSupport {

    @Test
    void 워크스페이스를_만들면_소유자가_되고_기본_폴더가_생긴다() throws Exception {
        Long workspaceOwner = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);

        call(workspaceOwner, getRequest("/api/workspaces"), null)
                .andExpect(jsonPath("$.data[0].workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.data[0].myRole").value("OWNER"))
                .andExpect(jsonPath("$.data[0].lastVisited").value(true));
        call(workspaceOwner, getRequest("/api/workspaces/" + workspaceId + "/folders"), null)
                .andExpect(jsonPath("$.data[0].name").value("기본 폴더"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void 이메일로_초대받은_계정은_승인_없이_바로_들어오고_다른_계정은_수락할_수_없다() throws Exception {
        Long owner = newUser();
        Long member = newUser();
        Long invitee = newUser();
        Long workspaceId = createWorkspace(owner);
        join(workspaceId, owner, member);
        String path = "/api/workspaces/" + workspaceId + "/invitations";
        String body = "{\"email\":\"USER%d@Example.com\"}".formatted(invitee);

        call(member, post(path), body).andExpect(status().isForbidden());
        call(owner, post(path), "{\"email\":\"메일 아님\"}").andExpect(status().isBadRequest());
        call(owner, post(path), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("user" + invitee + "@example.com"));
        String oldToken = invitationToken("user" + invitee + "@example.com");
        // 같은 이메일을 다시 초대하면 이전 링크는 못 쓴다
        call(owner, post(path), body).andExpect(status().isCreated());
        String token = invitationToken("user" + invitee + "@example.com");
        call(invitee, getRequest("/api/workspaces/invitations/" + oldToken), null)
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));

        call(member, getRequest("/api/workspaces/invitations/" + token), null)
                .andExpect(jsonPath("$.data.emailMatches").value(false));
        call(member, post("/api/workspaces/invitations/" + token + "/accept"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVITATION_EMAIL_MISMATCH"));
        call(invitee, getRequest("/api/workspaces/invitations/" + token), null)
                .andExpect(jsonPath("$.data.workspaceName").value("팀 워크스페이스"))
                .andExpect(jsonPath("$.data.emailMatches").value(true))
                .andExpect(jsonPath("$.data.member").value(false));
        call(invitee, post("/api/workspaces/invitations/" + token + "/accept"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("MEMBER"));

        call(invitee, getRequest("/api/workspaces/" + workspaceId), null).andExpect(status().isOk());
        call(invitee, post("/api/workspaces/invitations/" + token + "/accept"), null)
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    /** 초대 메일 이벤트에 담긴 토큰 (메일로만 가므로 응답에는 없다). 같은 이메일이면 가장 최근 것 */
    private String invitationToken(String email) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType().equals("MemberInvitedByEmail") && event.getPayload().contains(email))
                .reduce((first, second) -> second)
                .map(event -> com.jayway.jsonpath.JsonPath.<String>read(event.getPayload(), "$.payload.token"))
                .orElseThrow();
    }

    @Test
    void 소유자는_워크스페이스를_지우고_안의_폴더도_함께_지워진다() throws Exception {
        Long owner = newUser();
        Long member = newUser();
        Long workspaceId = createWorkspace(owner);
        join(workspaceId, owner, member);
        Long defaultFolder = defaultFolder(workspaceId, owner);
        Long project = createFolder(workspaceId, member, "기획", null);

        call(member, deleteRequest("/api/workspaces/" + workspaceId), null).andExpect(status().isForbidden());
        call(owner, deleteRequest("/api/workspaces/" + workspaceId), null).andExpect(status().isOk());

        call(member, getRequest("/api/workspaces"), null).andExpect(jsonPath("$.data.length()").value(0));
        call(owner, getRequest("/api/workspaces/" + workspaceId), null).andExpect(status().is4xxClientError());
        call(member, getRequest("/api/folders/" + project), null).andExpect(status().isNotFound());
        // 회의·회의록은 폴더 삭제와 같은 이벤트로 meeting·minutes가 지운다
        assertThat(outboxContains("FolderDeleted",
                "\"workspaceId\":%d,\"folderIds\":[%d,%d]".formatted(workspaceId, defaultFolder, project))).isTrue();
    }

    @Test
    void 멤버가_아니면_워크스페이스에_들어갈_수_없다() throws Exception {
        Long workspaceId = createWorkspace(newUser());

        call(newUser(), getRequest("/api/workspaces/" + workspaceId), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
    }

    @Test
    void 초대_링크로_승인_없이_바로_참여한다() throws Exception {
        Long workspaceOwner = newUser();
        Long guest = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        String token = data(call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/invite-link"), null), "$.data.token");

        call(guest, post("/api/invites/" + token + "/join"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.data.myRole").value("MEMBER"));
        call(guest, post("/api/invites/" + token + "/join"), null).andExpect(status().isOk());

        call(guest, getRequest("/api/workspaces/" + workspaceId), null).andExpect(jsonPath("$.data.myRole").value("MEMBER"));
        call(guest, getRequest("/api/invites/" + token), null).andExpect(jsonPath("$.data.myStatus").value("MEMBER"));
        call(newUser(), post("/api/invites/wrong-token/join"), null)
                .andExpect(jsonPath("$.code").value("INVITE_LINK_INVALID"));
    }

    /** REQ-016~017·035 (레거시): 참여 요청 → 승인 */
    @Test
    void 초대_링크로_요청하고_소유자가_승인하면_멤버가_된다() throws Exception {
        Long workspaceOwner = newUser();
        Long guest = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        String token = data(call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/invite-link"), null)
                .andExpect(jsonPath("$.data.inviteUrl").value(startsWith("http://localhost:5173/invites/"))), "$.data.token");

        call(guest, getRequest("/api/invites/" + token), null).andExpect(jsonPath("$.data.myStatus").value("NONE"));
        Long requestId = id(call(guest, post("/api/invites/" + token + "/join-requests"), null)
                .andExpect(status().isCreated()), "$.data.joinRequestId");
        call(guest, getRequest("/api/invites/" + token), null).andExpect(jsonPath("$.data.myStatus").value("PENDING"));
        call(guest, post("/api/invites/" + token + "/join-requests"), null)
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_PENDING"));
        assertThat(outboxContains("MemberJoinRequested", "\"requesterUserId\":" + guest)).isTrue();

        call(workspaceOwner, getRequest("/api/workspaces/" + workspaceId + "/join-requests"), null)
                .andExpect(jsonPath("$.data[0].userId").value(guest))
                .andExpect(jsonPath("$.data[0].email").value("user" + guest + "@example.com"));
        call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/join-requests/" + requestId + "/approve"), null)
                .andExpect(status().isOk());

        call(guest, getRequest("/api/workspaces/" + workspaceId), null).andExpect(jsonPath("$.data.myRole").value("MEMBER"));
        call(guest, getRequest("/api/invites/" + token), null).andExpect(jsonPath("$.data.myStatus").value("MEMBER"));
        assertThat(outboxContains("MemberJoinApproved", "\"userId\":" + guest)).isTrue();
        call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/join-requests/" + requestId + "/approve"), null)
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    void 거절되면_다시_요청할_수_있다() throws Exception {
        Long workspaceOwner = newUser();
        Long guest = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        String token = data(call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/invite-link"), null), "$.data.token");
        Long requestId = id(call(guest, post("/api/invites/" + token + "/join-requests"), null), "$.data.joinRequestId");

        call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/join-requests/" + requestId + "/reject"), null)
                .andExpect(status().isOk());

        call(guest, getRequest("/api/invites/" + token), null).andExpect(jsonPath("$.data.myStatus").value("REJECTED"));
        call(guest, post("/api/invites/" + token + "/join-requests"), null).andExpect(status().isCreated());
    }

    @Test
    void 초대_링크는_1일_뒤_만료되고_새로_발급하면_이전_링크는_쓸_수_없다() throws Exception {
        Long workspaceOwner = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        String first = data(call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/invite-link"), null), "$.data.token");
        String second = data(call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/invite-link"), null), "$.data.token");

        call(newUser(), getRequest("/api/invites/" + first), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_LINK_INVALID"));
        call(newUser(), getRequest("/api/invites/" + second), null).andExpect(status().isOk());

        clock.advance(Duration.ofDays(1));
        call(newUser(), getRequest("/api/invites/" + second), null)
                .andExpect(jsonPath("$.code").value("INVITE_LINK_INVALID"));
    }

    @Test
    void 멤버_목록은_멤버_누구나_보고_초대_링크는_소유자만_다룬다() throws Exception {
        Long workspaceOwner = newUser();
        Long member = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        join(workspaceId, workspaceOwner, member);

        call(workspaceOwner, getRequest("/api/workspaces/" + workspaceId + "/members"), null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[1].email").value("user" + member + "@example.com"));
        call(member, getRequest("/api/workspaces/" + workspaceId + "/members"), null)
                .andExpect(jsonPath("$.data.length()").value(2));
        call(newUser(), getRequest("/api/workspaces/" + workspaceId + "/members"), null)
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
        call(member, post("/api/workspaces/" + workspaceId + "/invite-link"), null).andExpect(status().isForbidden());
    }

    @Test
    void 소유자를_양도하면_넘긴_사람은_일반_멤버가_된다() throws Exception {
        Long workspaceOwner = newUser();
        Long member = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        join(workspaceId, workspaceOwner, member);

        call(workspaceOwner, putRequest("/api/workspaces/" + workspaceId + "/owner"), "{\"userId\":%d}".formatted(member))
                .andExpect(status().isOk());

        call(member, getRequest("/api/workspaces/" + workspaceId), null).andExpect(jsonPath("$.data.myRole").value("OWNER"));
        call(workspaceOwner, getRequest("/api/workspaces/" + workspaceId), null).andExpect(jsonPath("$.data.myRole").value("MEMBER"));
        call(workspaceOwner, post("/api/workspaces/" + workspaceId + "/invite-link"), null).andExpect(status().isForbidden());
    }

    @Test
    void 소유자와_폴더_소유자는_양도한_뒤에야_나갈_수_있다() throws Exception {
        Long workspaceOwner = newUser();
        Long owner = newUser();
        Long plain = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        join(workspaceId, workspaceOwner, owner);
        join(workspaceId, workspaceOwner, plain);
        createFolder(workspaceId, owner, "기획", null);

        call(workspaceOwner, deleteRequest("/api/workspaces/" + workspaceId + "/members/me"), null)
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
        call(owner, deleteRequest("/api/workspaces/" + workspaceId + "/members/me"), null)
                .andExpect(jsonPath("$.code").value("FOLDER_OWNERSHIP_REMAINING"));
        call(plain, deleteRequest("/api/workspaces/" + workspaceId + "/members/me"), null).andExpect(status().isOk());

        call(plain, getRequest("/api/workspaces/" + workspaceId), null).andExpect(status().isForbidden());
    }

    @Test
    void 내보낸_멤버의_폴더는_가장_먼저_초대된_멤버에게_없으면_소유자에게_넘어간다() throws Exception {
        Long workspaceOwner = newUser();
        Long owner = newUser();
        Long first = newUser();
        Long second = newUser();
        Long workspaceId = createWorkspace(workspaceOwner);
        for (Long user : new Long[] {owner, first, second}) {
            join(workspaceId, workspaceOwner, user);
        }
        Long shared = createFolder(workspaceId, owner, "공유", null);
        Long alone = createFolder(workspaceId, owner, "혼자", null);
        inviteToFolder(shared, workspaceOwner, first);
        inviteToFolder(shared, workspaceOwner, second);

        call(workspaceOwner, deleteRequest("/api/workspaces/" + workspaceId + "/members/" + owner), null).andExpect(status().isOk());

        call(first, getRequest("/api/folders/" + shared), null).andExpect(jsonPath("$.data.isOwner").value(true));
        call(workspaceOwner, getRequest("/api/folders/" + alone), null).andExpect(jsonPath("$.data.isOwner").value(true));
        call(owner, getRequest("/api/folders/" + shared), null).andExpect(status().isForbidden());
    }
}
