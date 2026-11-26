package com.nineouting.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.common.client.UserSummary;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * user-service를 기다리는 동안 DB 연결을 쥐고 있지 않는지 (2026-11-26 부하 시험).
 * 가짜 클라이언트가 불릴 때 사용 중인 연결 수를 잰다. 시험은 한 번에 하나씩 돌아 다른 요청이 연결을 쓰지 않는다
 */
class RemoteCallConnectionTest extends ApiTestSupport {

    @Autowired
    DataSource dataSource;

    private final List<Integer> activeDuringRemoteCall = new ArrayList<>();
    private Long owner;
    private Long workspaceId;
    private Long folderId;

    @BeforeEach
    void recordConnectionsDuringRemoteCalls() throws Exception {
        owner = newUser();
        workspaceId = createWorkspace(owner);
        folderId = createFolder(workspaceId, owner, "기획", null);

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        given(userClient.findUsers(anyCollection(), any())).willAnswer(invocation -> {
            activeDuringRemoteCall.add(hikari.getHikariPoolMXBean().getActiveConnections());
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(),
                    id -> new UserSummary(id, "user" + id, "user" + id + "@example.com", null), (a, b) -> a));
        });
        activeDuringRemoteCall.clear();
    }

    @Test
    void 폴더_멤버() throws Exception {
        call(owner, getRequest("/api/folders/" + folderId + "/members"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    @Test
    void 워크스페이스_멤버() throws Exception {
        call(owner, getRequest("/api/workspaces/" + workspaceId + "/members"), null).andExpect(status().isOk());
        assertNoConnectionHeld();
    }

    private void assertNoConnectionHeld() {
        assertThat(activeDuringRemoteCall).isNotEmpty().allMatch(active -> active == 0);
    }
}
