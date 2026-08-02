package org.example.common.redis.codec;

import org.example.common.exception.InfrastructureException;

/**
 * Redis 캐시 codec 직렬화/역직렬화 실패. 모듈별 캐시 예외 대신 공통 codec 헬퍼가 던진다.
 * {@link InfrastructureException} 하위라 기존 인프라 예외 매핑(5xx)과 동일하게 처리된다.
 */
public class RedisCodecException extends InfrastructureException {

    public RedisCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
