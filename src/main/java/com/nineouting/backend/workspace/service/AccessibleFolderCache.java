package com.nineouting.backend.workspace.service;

import com.nineouting.backend.workspace.dto.AccessibleFolderResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사용자가 들어갈 수 있는 폴더(meeting, minutes가 자주 부른다)를 Redis에 잠깐 둔다 (2026-11-25 부하 시험 후속).
 *
 * <p>폴더, 폴더 멤버, 워크스페이스, 워크스페이스 멤버 중 하나라도 바뀌면 커밋 뒤 버전을 올린다.
 * 키에 버전이 들어 있어 바뀐 뒤에는 모두 새로 읽고, 예전 버전 키는 TTL로 사라진다.
 * 권한이 바뀌는 경우가 많아(소유자, 상위 폴더, 접근 범위 등) 사용자별로 지우지 않고 한꺼번에 버린다.
 * Redis가 안 되면 캐시 없이 DB에서 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessibleFolderCache {

    private static final String VERSION_KEY = "accessible-folders:version";
    private static final String KEY = "accessible-folders:";
    static final Duration TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<AccessibleFolderResponse>> TYPE = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    /** 버전을 DB보다 먼저 읽는다. 그래야 바뀐 뒤의 버전으로 바뀌기 전 값을 넣는 일이 없다 */
    public List<AccessibleFolderResponse> get(Long userId, Supplier<List<AccessibleFolderResponse>> loader) {
        String key;
        try {
            String version = redis.opsForValue().get(VERSION_KEY);
            key = KEY + (version == null ? "0" : version) + ":" + userId;
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return jsonMapper.readValue(cached, TYPE);
            }
        } catch (DataAccessException | JacksonException e) {
            log.warn("폴더 캐시를 읽지 못해 DB에서 읽습니다: {}", e.getMessage());
            return loader.get();
        }
        List<AccessibleFolderResponse> loaded = loader.get();
        try {
            redis.opsForValue().set(key, jsonMapper.writeValueAsString(loaded), TTL);
        } catch (DataAccessException | JacksonException e) {
            log.warn("폴더 캐시에 넣지 못했습니다: {}", e.getMessage());
        }
        return loaded;
    }

    /** 한 트랜잭션에서 여러 번 불려도 커밋 뒤 한 번만 올린다 */
    public void changed() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            bump();
            return;
        }
        if (TransactionSynchronizationManager.hasResource(this)) {
            return;
        }
        TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bump();
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(AccessibleFolderCache.this);
            }
        });
    }

    private void bump() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (DataAccessException e) {
            // 올리지 못해도 TTL 뒤에는 새 값이 보인다
            log.warn("폴더 캐시 버전을 올리지 못했습니다: {}", e.getMessage());
        }
    }
}
