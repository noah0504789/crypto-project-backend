package org.example.common.time;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 서비스 표준 시간대({@link #ZONE_ID}) 기준 {@code LocalDateTime} ↔ {@code Instant} 변환 유틸이자 존 상수의 단일 출처.
 *
 * <p>도메인/영속/캐시 각지에 흩어져 있던 {@code atZone(ZONE_ID).toInstant()} /
 * {@code LocalDateTime.ofInstant(..., ZONE_ID)} null-guard 변환을 한곳으로 모은다. null 입력은 null 반환한다.
 * 원시 {@code ZoneId} 가 필요하면 {@link #ZONE_ID} 를 직접 참조한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceTimeConverter {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    public static Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZONE_ID).toInstant();
    }

    public static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZONE_ID);
    }

    /** null 이면 0을 반환한다(epoch millis 를 항상 long 으로 받고 싶은 조회용). */
    public static long toEpochMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.atZone(ZONE_ID).toInstant().toEpochMilli();
    }
}
