package org.example.marketdetection.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.example.common.enums.PriceAlertChangeRateThreshold;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceChangeUnitTest {

    @Test
    @DisplayName("표본이 없으면 현재가를 평균으로 보고 변동률은 0이다")
    void of_withoutSamples_usesCurrentPriceAsAverage() {
        // given & when
        PriceChange priceChange = PriceChange.of(100.0, List.of());

        // then
        assertThat(priceChange.averagePrice()).isEqualTo(100.0);
        assertThat(priceChange.changeRate()).isZero();
    }

    @Test
    @DisplayName("표본 평균 대비 변동률을 계산한다")
    void of_withSamples_calculatesChangeRate() {
        // given
        List<PricePoint> points =
                List.of(new PricePoint(100.0, 1L), new PricePoint(100.0, 2L));

        // when
        PriceChange priceChange = PriceChange.of(110.0, points);

        // then
        assertThat(priceChange.averagePrice()).isEqualTo(100.0);
        assertThat(priceChange.changeRate()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("가격이 없는 표본은 평균에서 제외한다")
    void of_ignoresSamplesWithoutPrice() {
        // given
        List<PricePoint> points =
                List.of(new PricePoint(100.0, 1L), new PricePoint(null, 2L));

        // when
        PriceChange priceChange = PriceChange.of(100.0, points);

        // then
        assertThat(priceChange.averagePrice()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("변동률이 넘는 임계를 모두 반환한다")
    void matchedThresholds_returnsEveryExceededThreshold() {
        // given
        List<PricePoint> points = List.of(new PricePoint(100.0, 1L));

        // when
        PriceChange priceChange = PriceChange.of(106.0, points);

        // then
        assertThat(priceChange.matchedThresholds())
                .contains(
                        PriceAlertChangeRateThreshold.PERCENT_3,
                        PriceAlertChangeRateThreshold.PERCENT_5)
                .doesNotContain(PriceAlertChangeRateThreshold.PERCENT_7);
    }
}
