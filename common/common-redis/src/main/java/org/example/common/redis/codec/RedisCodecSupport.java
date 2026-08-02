package org.example.common.redis.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Redis hash codec 구현들이 공통으로 쓰던 필드 인코딩/디코딩 헬퍼 모음.
 *
 * <p>각 codec 에 흩어져 있던 {@code str/nullable/parseInstant/parseEnum/parseLongOrDefault} 와
 * JSON 직렬화·역직렬화(모듈별 예외로 throw)를 한곳으로 모은다. JSON 실패는 공통 {@link RedisCodecException}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisCodecSupport {

    /** null → "" (hash 필드는 빈 문자열로 저장). */
    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** null/blank → null (도메인 복원 시 미설정 표현). */
    public static String nullable(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw;
    }

    public static Instant parseInstant(String raw) {
        return (raw == null || raw.isBlank()) ? null : Instant.parse(raw);
    }

    public static <E extends Enum<E>> E parseEnum(String raw, Class<E> enumType) {
        return (raw == null || raw.isBlank()) ? null : Enum.valueOf(enumType, raw);
    }

    public static Long parseLongOrDefault(String raw, Long defaultValue) {
        return (raw == null || raw.isBlank()) ? defaultValue : Long.valueOf(raw);
    }

    /** 객체를 JSON 문자열로. null → "". 실패 시 {@link RedisCodecException}. */
    public static String toJson(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RedisCodecException("[redis] json 직렬화 실패", e);
        }
    }

    /** JSON 문자열을 대상 타입으로. null/blank → defaultValue. 실패 시 {@link RedisCodecException}. */
    public static <T> T fromJson(ObjectMapper objectMapper, String raw, TypeReference<T> typeReference, T defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(raw, typeReference);
        } catch (JsonProcessingException e) {
            throw new RedisCodecException("[redis] json 역직렬화 실패", e);
        }
    }
}
