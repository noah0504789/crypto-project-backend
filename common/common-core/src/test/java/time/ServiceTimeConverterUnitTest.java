package time;

import org.example.common.time.ServiceTimeConverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTimeConverterUnitTest {

    @Test
    @DisplayName("LocalDateTime ↔ Instant 를 서비스 존 기준으로 왕복 변환한다")
    void roundTrip() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 1, 1, 0, 0);

        Instant instant = ServiceTimeConverter.toInstant(dateTime);

        assertThat(instant).isEqualTo(dateTime.atZone(ServiceTimeConverter.ZONE_ID).toInstant());
        assertThat(ServiceTimeConverter.toLocalDateTime(instant)).isEqualTo(dateTime);
    }

    @Test
    @DisplayName("toEpochMillis 는 서비스 존 기준 epoch millis 를 반환한다")
    void epochMillis() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 1, 1, 0, 0);

        assertThat(ServiceTimeConverter.toEpochMillis(dateTime))
                .isEqualTo(dateTime.atZone(ServiceTimeConverter.ZONE_ID).toInstant().toEpochMilli());
    }

    @Test
    @DisplayName("null 입력은 null(또는 epoch millis 0)을 반환한다")
    void nullSafe() {
        assertThat(ServiceTimeConverter.toInstant(null)).isNull();
        assertThat(ServiceTimeConverter.toLocalDateTime(null)).isNull();
        assertThat(ServiceTimeConverter.toEpochMillis(null)).isZero();
    }
}
