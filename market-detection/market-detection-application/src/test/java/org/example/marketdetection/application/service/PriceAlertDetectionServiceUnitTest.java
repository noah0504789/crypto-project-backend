package org.example.marketdetection.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.util.List;
import org.example.common.time.Clock;
import org.example.marketdetection.application.properties.PriceAlertDetectionProperties;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.application.dto.PricePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceAlertDetectionServiceUnitTest {

    private static final String CODE = "KRW-BTC";

    @Mock private Clock clock;

    private PriceAlertDetectionService sut;

    @BeforeEach
    void setUp() {
        PriceAlertDetectionProperties properties =
                new PriceAlertDetectionProperties(
                        3,
                        Duration.ofSeconds(10),
                        new PriceAlertDetectionProperties.Store(
                                "upbit-ticker-store", Duration.ofMinutes(3), Duration.ofMinutes(3), false));

        sut = new PriceAlertDetectionService(properties, clock);
    }

    @Test
    @DisplayName("허용 시간이 지난 표본은 stale 로 본다")
    void isStale_whenSampleIsTooOld() {
        // given
        given(clock.nowMs()).willReturn(20_000L);

        // when & then
        assertThat(sut.isStale(5_000L)).isTrue();
        assertThat(sut.isStale(15_000L)).isFalse();
    }

    @Test
    @DisplayName("임계를 넘으면 임계마다 탐지 이벤트를 만든다")
    void detect_createsEventPerMatchedThreshold() {
        // given
        PricePoint pricePoint = new PricePoint(110.0, 3_000L);
        List<PricePoint> recentPoints = List.of(new PricePoint(100.0, 1_000L));

        // when
        List<PriceAlertDetectedEvent> events = sut.detect(CODE, pricePoint, recentPoints);

        // then
        assertThat(events)
                .extracting(PriceAlertDetectedEvent::getThreshold)
                .containsExactlyInAnyOrder("PERCENT_0", "PERCENT_3", "PERCENT_5", "PERCENT_7");

        assertThat(events)
                .allSatisfy(
                        event -> {
                            assertThat(event.getCode()).isEqualTo(CODE);
                            assertThat(event.getPrice()).isEqualTo(110.0);
                            assertThat(event.getAvgPrice()).isEqualTo(100.0);
                            assertThat(event.getTimestamp()).isEqualTo(3_000L);
                            assertThat(event.getAvgInterval()).isEqualTo(3);
                        });
    }
}
