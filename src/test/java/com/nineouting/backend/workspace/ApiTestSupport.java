package com.nineouting.backend.workspace;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.common.outbox.OutboxEventRepository;
import com.nineouting.backend.workspace.TestcontainersConfiguration.MutableClock;
import com.nineouting.backend.workspace.client.UserClient;
import com.nineouting.backend.workspace.client.UserSummary;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 MySQL·Kafka와 security-common 토큰 검증을 거치는 API 테스트의 공통 도우미.
 * user-service는 띄우지 않고 UserClient를 가짜로 바꾼다 (이름: user{ID}, 이메일: user{ID}@example.com).
 */
// outbox는 보낸 이벤트를 바로 지우므로, 발행한 이벤트를 테이블에서 확인하려고 테스트에서는 전송을 멈춘다
@SpringBootTest(properties = "nineouting.outbox.relay-interval=1h")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    /** 테스트마다 겹치지 않는 사용자 ID */
    private static final AtomicLong NEXT_USER_ID = new AtomicLong(1000);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected OutboxEventRepository outboxEventRepository;

    @MockitoBean
    protected UserClient userClient;

    @BeforeEach
    void fakeUserService() {
        given(userClient.findUsers(anyCollection())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(),
                    id -> new UserSummary(id, "user" + id, "user" + id + "@example.com", null), (a, b) -> a));
        });
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    protected static Long newUser() {
        return NEXT_USER_ID.incrementAndGet();
    }

    protected Long createWorkspace(Long adminId) throws Exception {
        return id(call(adminId, post("/api/workspaces"), "{\"name\":\"팀 워크스페이스\"}")
                .andExpect(status().isCreated()), "$.data.workspaceId");
    }

    /** 초대 링크 → 참여 요청 → 관리자 승인 */
    protected void join(Long workspaceId, Long adminId, Long userId) throws Exception {
        String token = data(call(adminId, post("/api/workspaces/" + workspaceId + "/invite-link"), null), "$.data.token");
        Long requestId = id(call(userId, post("/api/invites/" + token + "/join-requests"), null)
                .andExpect(status().isCreated()), "$.data.joinRequestId");
        call(adminId, post("/api/workspaces/" + workspaceId + "/join-requests/" + requestId + "/approve"), null)
                .andExpect(status().isOk());
    }

    protected Long createFolder(Long workspaceId, Long userId, String name, Long parentId) throws Exception {
        String body = parentId == null
                ? "{\"name\":\"%s\"}".formatted(name)
                : "{\"name\":\"%s\",\"parentId\":%d}".formatted(name, parentId);
        return id(call(userId, post("/api/workspaces/" + workspaceId + "/folders"), body)
                .andExpect(status().isCreated()), "$.data.folderId");
    }

    protected Long defaultFolder(Long workspaceId, Long userId) throws Exception {
        List<Map<String, Object>> folders = JsonPath.read(call(userId, get("/api/workspaces/" + workspaceId + "/folders"), null)
                .andReturn().getResponse().getContentAsString(), "$.data[?(@.isDefault == true)]");
        return ((Number) folders.getFirst().get("folderId")).longValue();
    }

    protected void inviteToFolder(Long folderId, Long ownerId, Long userId) throws Exception {
        call(ownerId, post("/api/folders/" + folderId + "/members"), "{\"userId\":%d}".formatted(userId))
                .andExpect(status().isOk());
    }

    protected ResultActions call(Long userId, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestcontainersConfiguration.token(userId));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    protected static MockHttpServletRequestBuilder getRequest(String url) {
        return get(url);
    }

    protected static MockHttpServletRequestBuilder patchRequest(String url) {
        return patch(url);
    }

    protected static MockHttpServletRequestBuilder putRequest(String url) {
        return put(url);
    }

    protected static MockHttpServletRequestBuilder deleteRequest(String url) {
        return delete(url);
    }

    protected boolean outboxContains(String eventType, String text) {
        return outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getEventType().equals(eventType) && event.getPayload().contains(text));
    }

    protected static Long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    protected static <T> T data(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
