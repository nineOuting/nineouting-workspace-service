package com.nineouting.backend.workspace.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.workspace.config.WorkspaceProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** user-service 대신 JDK HTTP 서버를 띄워 실제 HTTP 호출과 응답 해석을 확인한다. */
class UserClientTest {

    private final List<Integer> requestedBatchSizes = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 사용자_정보를_조회하고_100명씩_나눠_요청한다() throws IOException {
        UserClient client = clientFor(startUserService());

        Map<Long, UserSummary> users = client.findUsers(LongStream.rangeClosed(1, 150).boxed().toList());

        assertThat(users).hasSize(150);
        assertThat(users.get(7L)).isEqualTo(new UserSummary(7L, "user7", "user7@example.com", null));
        assertThat(requestedBatchSizes).containsExactly(100, 50);
    }

    @Test
    void user_service에_연결할_수_없으면_503이다() {
        UserClient client = new UserClient(RestClient.builder(),
                new WorkspaceProperties("http://localhost:5173", Duration.ofDays(1), "http://127.0.0.1:1"));

        assertThatThrownBy(() -> client.findUsers(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo("USER_SERVICE_UNAVAILABLE");
    }

    private String startUserService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/users", exchange -> {
            String ids = exchange.getRequestURI().getQuery().replace("ids=", "");
            List<String> idList = Arrays.asList(ids.split(","));
            requestedBatchSizes.add(idList.size());
            String data = idList.stream()
                    .map(id -> "{\"userId\":%s,\"name\":\"user%s\",\"email\":\"user%s@example.com\",\"profileImageUrl\":null}"
                            .formatted(id, id, id))
                    .collect(Collectors.joining(","));
            byte[] body = ("{\"success\":true,\"data\":[" + data + "],\"message\":\"\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static UserClient clientFor(String baseUrl) {
        return new UserClient(RestClient.builder(), new WorkspaceProperties("http://localhost:5173", Duration.ofDays(1), baseUrl));
    }
}
