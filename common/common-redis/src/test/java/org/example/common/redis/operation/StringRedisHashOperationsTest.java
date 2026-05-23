package org.example.common.redis.operation;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StringRedisHashOperationsTest {

    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    private final HashOperations<String, String, String> hashOperations = mock(HashOperations.class);
    private final StringRedisHashOperations operations;

    @SuppressWarnings("unchecked")
    StringRedisHashOperationsTest() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        operations = new StringRedisHashOperations(redisTemplate);
    }

    @Test
    void saveStoresAllFields() {
        Map<String, String> values = Map.of("field", "value");

        operations.save("key", values);

        verify(hashOperations).putAll("key", values);
    }

    @Test
    void findReturnsStringMapCopy() {
        when(hashOperations.entries("key")).thenReturn(Map.of("field", "value"));

        Map<String, String> result = operations.find("key");

        assertThat(result).containsEntry("field", "value");
    }

    @Test
    void hasEntriesChecksEntries() {
        when(hashOperations.entries("key")).thenReturn(Map.of("field", "value"));

        assertThat(operations.hasEntries("key")).isTrue();
    }

    @Test
    void updateConvertsNullValueToEmptyString() {
        operations.update("key", "field", null);

        verify(hashOperations).put("key", "field", "");
    }

    @Test
    void delegatesHashOperations() {
        when(hashOperations.get("key", "field")).thenReturn("value");
        when(hashOperations.size("key")).thenReturn(1L);
        when(hashOperations.values("key")).thenReturn(List.of("value"));

        assertThat(operations.findField("key", "field")).isEqualTo("value");
        assertThat(operations.size("key")).isEqualTo(1L);
        assertThat(operations.values("key")).containsExactly("value");

        operations.deleteField("key", "field");

        verify(hashOperations).delete("key", "field");
    }
}
