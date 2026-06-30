package org.example.websocket.gateway.session.adapter.out.redis;

import org.example.common.redis.operation.StringRedisHashOperations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

import static org.example.common.enums.RedisKey.SESSION_INFO;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSessionLocationAdapterTest {

    @Mock
    private StringRedisHashOperations hash;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private RedisSessionLocationAdapter sut;

    private final String userId = "user-1";
    private final String sessionId = "session-1";
    private final String serverId = "server-1";
    private final String otherServerId = "server-2";

    private final String sessionInfoKey = SESSION_INFO.keyFor(userId);

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("세션 위치를 저장하고 TTL을 갱신한다")
        void save() {
            // when
            sut.save(userId, sessionId, serverId);

            // then
            InOrder inOrder = inOrder(hash, redisTemplate);

            inOrder.verify(hash).update(sessionInfoKey, sessionId, serverId);
            inOrder.verify(redisTemplate).expire(sessionInfoKey, Duration.ofMinutes(3));
        }
    }

    @Nested
    @DisplayName("deleteIfServerMatches")
    class DeleteIfServerMatchesTest {

        @Test
        @DisplayName("현재 serverId가 기대 serverId와 같으면 세션을 삭제한다")
        void deleteIfServerMatches() {
            // given
            given(hash.findField(sessionInfoKey, sessionId))
                    .willReturn(serverId);

            given(hash.size(sessionInfoKey))
                    .willReturn(1L);

            // when
            sut.deleteIfServerMatches(userId, sessionId, serverId);

            // then
            InOrder inOrder = inOrder(hash, redisTemplate);

            inOrder.verify(hash).findField(sessionInfoKey, sessionId);
            inOrder.verify(hash).deleteField(sessionInfoKey, sessionId);
            inOrder.verify(hash).size(sessionInfoKey);

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("현재 serverId가 기대 serverId와 다르면 삭제하지 않는다")
        void deleteIfServerDoesNotMatch() {
            // given
            given(hash.findField(sessionInfoKey, sessionId))
                    .willReturn(otherServerId);

            // when
            sut.deleteIfServerMatches(userId, sessionId, serverId);

            // then
            verify(hash).findField(sessionInfoKey, sessionId);
            verify(hash, never()).deleteField(anyString(), anyString());
            verify(hash, never()).size(anyString());
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("현재 serverId가 없으면 삭제하지 않는다")
        void deleteIfCurrentServerIdIsNull() {
            // given
            given(hash.findField(sessionInfoKey, sessionId))
                    .willReturn(null);

            // when
            sut.deleteIfServerMatches(userId, sessionId, serverId);

            // then
            verify(hash).findField(sessionInfoKey, sessionId);
            verify(hash, never()).deleteField(anyString(), anyString());
            verify(hash, never()).size(anyString());
            verify(redisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTest {

        @Test
        @DisplayName("세션 필드를 삭제하고 남은 세션이 있으면 key는 삭제하지 않는다")
        void deleteWhenHashStillHasSessions() {
            // given
            given(hash.size(sessionInfoKey))
                    .willReturn(1L);

            // when
            sut.delete(userId, sessionId);

            // then
            InOrder inOrder = inOrder(hash, redisTemplate);

            inOrder.verify(hash).deleteField(sessionInfoKey, sessionId);
            inOrder.verify(hash).size(sessionInfoKey);

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("세션 필드를 삭제한 뒤 남은 세션이 없으면 key를 삭제한다")
        void deleteWhenHashIsEmpty() {
            // given
            given(hash.size(sessionInfoKey))
                    .willReturn(0L);

            // when
            sut.delete(userId, sessionId);

            // then
            InOrder inOrder = inOrder(hash, redisTemplate);

            inOrder.verify(hash).deleteField(sessionInfoKey, sessionId);
            inOrder.verify(hash).size(sessionInfoKey);
            inOrder.verify(redisTemplate).delete(sessionInfoKey);
        }

        @Test
        @DisplayName("hash size가 null이면 key를 삭제하지 않는다")
        void deleteWhenHashSizeIsNull() {
            // given
            given(hash.size(sessionInfoKey))
                    .willReturn(null);

            // when
            sut.delete(userId, sessionId);

            // then
            verify(hash).deleteField(sessionInfoKey, sessionId);
            verify(hash).size(sessionInfoKey);
            verify(redisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("refreshTtl")
    class RefreshTtlTest {

        @Test
        @DisplayName("세션 정보 key의 TTL을 3분으로 갱신한다")
        void refreshTtl() {
            // when
            sut.refreshTtl(userId);

            // then
            verify(redisTemplate).expire(sessionInfoKey, Duration.ofMinutes(3));
        }
    }
}