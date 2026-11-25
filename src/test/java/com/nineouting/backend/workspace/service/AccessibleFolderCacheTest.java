package com.nineouting.backend.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nineouting.backend.workspace.dto.AccessibleFolderResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Redis가 멈춰도 DB에서 읽고, 한 트랜잭션의 여러 변경은 커밋 뒤 버전을 한 번만 올린다 */
class AccessibleFolderCacheTest {

    private static final List<AccessibleFolderResponse> FOLDERS =
            List.of(new AccessibleFolderResponse(1L, 2L, "팀", null, "기본 폴더"));

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AccessibleFolderCache cache = new AccessibleFolderCache(redis, JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void Redis를_읽지_못하면_DB에서_읽는다() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(cache.get(5L, () -> FOLDERS)).isEqualTo(FOLDERS);
    }

    @Test
    void 넣지_못해도_읽은_값을_준다() {
        doThrow(new RedisConnectionFailureException("down")).when(values).set(anyString(), anyString(), any(Duration.class));
        assertThat(cache.get(5L, () -> FOLDERS)).isEqualTo(FOLDERS);
        verify(values).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void 한_트랜잭션에서_여러_번_바뀌어도_커밋_뒤_한_번만_올린다() {
        TransactionSynchronizationManager.initSynchronization();
        cache.changed();
        cache.changed();
        verify(values, times(0)).increment("accessible-folders:version");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(values).increment("accessible-folders:version");
        assertThat(TransactionSynchronizationManager.hasResource(cache)).isFalse();
    }

    @Test
    void 트랜잭션_밖에서는_바로_올리고_실패해도_넘어간다() {
        when(values.increment("accessible-folders:version")).thenThrow(new RedisConnectionFailureException("down"));
        assertThatCode(cache::changed).doesNotThrowAnyException();
        verify(values).increment("accessible-folders:version");
    }
}
