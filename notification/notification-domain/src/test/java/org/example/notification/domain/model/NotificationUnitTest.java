package org.example.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationUnitTest {

    private static final String ID = "notification-id-1";
    private static final String MARKET_CODE = "KRW-BTC";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Nested
    @DisplayName("createPriceAlert")
    class CreatePriceAlertTest {

        @Test
        @DisplayName("양수 changeRate이면 상승 가격 알림을 생성한다")
        void createPriceAlert_should_create_increased_price_alert() {
            // given
            Map<String, Object> payload = Map.of(
                    "code", MARKET_CODE,
                    "changeRate", 0.05
            );

            // when
            Notification notification = Notification.createPriceAlert(
                    ID,
                    MARKET_CODE,
                    2_671_000D,
                    2_660_000D,
                    5,
                    0.05,
                    payload,
                    CREATED_AT
            );

            // then
            assertThat(notification.getId()).isEqualTo(ID);
            assertThat(notification.getType()).isEqualTo(NotificationType.PRICE_ALERT);
            assertThat(notification.getTitle()).isEqualTo("가격 알림");
            assertThat(notification.getMessage()).isEqualTo("KRW-BTC 현재가 2,671,000원, 최근 5분간 평균가 2,660,000원 대비 5.0% 상승했습니다.");
            assertThat(notification.getLink()).isNull();
            assertThat(notification.getPayload()).containsAllEntriesOf(payload);
            assertThat(notification.isDeleted()).isFalse();
            assertThat(notification.getDeletedAt()).isNull();
            assertThat(notification.getCreatedAt()).isEqualTo(CREATED_AT);

            assertThat(notification.getMessageParts())
                    .extracting(NotificationMessagePart::text)
                    .containsExactly(
                            "KRW-BTC",
                            " 현재가 ",
                            "2,671,000원",
                            ", 최근 5분간 평균가 ",
                            "2,660,000원",
                            " 대비 ",
                            "5.0%",
                            " ",
                            "상승",
                            "했습니다."
                    );
        }

        @Test
        @DisplayName("현재가와 감시 구간 평균가를 가격 알림 본문에 포함한다")
        void createPriceAlert_should_include_current_and_average_prices() {
            Notification notification = Notification.createPriceAlert(
                    ID,
                    MARKET_CODE,
                    2_671_000D,
                    2_660_000D,
                    5,
                    0.0041353383D,
                    Map.of(),
                    CREATED_AT
            );

            assertThat(notification.getMessage())
                    .isEqualTo("KRW-BTC 현재가 2,671,000원, 최근 5분간 평균가 2,660,000원 대비 0.4% 상승했습니다.");
        }

        @Test
        @DisplayName("음수 changeRate이면 하락 가격 알림을 생성한다")
        void createPriceAlert_should_create_decreased_price_alert() {
            // given
            Map<String, Object> payload = Map.of(
                    "code", MARKET_CODE,
                    "changeRate", -0.037
            );

            // when
            Notification notification = Notification.createPriceAlert(
                    ID,
                    MARKET_CODE,
                    2_671_000D,
                    2_660_000D,
                    5,
                    -0.037,
                    payload,
                    CREATED_AT
            );

            // then
            assertThat(notification.getId()).isEqualTo(ID);
            assertThat(notification.getType()).isEqualTo(NotificationType.PRICE_ALERT);
            assertThat(notification.getTitle()).isEqualTo("가격 알림");
            assertThat(notification.getMessage()).isEqualTo("KRW-BTC 현재가 2,671,000원, 최근 5분간 평균가 2,660,000원 대비 3.7% 하락했습니다.");

            assertThat(notification.getMessageParts())
                    .extracting(NotificationMessagePart::text)
                    .containsExactly(
                            "KRW-BTC",
                            " 현재가 ",
                            "2,671,000원",
                            ", 최근 5분간 평균가 ",
                            "2,660,000원",
                            " 대비 ",
                            "3.7%",
                            " ",
                            "하락",
                            "했습니다."
                    );
        }

        @Test
        @DisplayName("changeRate가 null이면 0.0% 상승 가격 알림을 생성한다")
        void createPriceAlert_should_treat_null_change_rate_as_zero() {
            // when
            Notification notification = Notification.createPriceAlert(
                    ID,
                    MARKET_CODE,
                    2_671_000D,
                    2_660_000D,
                    5,
                    null,
                    null,
                    CREATED_AT
            );

            // then
            assertThat(notification.getId()).isEqualTo(ID);
            assertThat(notification.getType()).isEqualTo(NotificationType.PRICE_ALERT);
            assertThat(notification.getTitle()).isEqualTo("가격 알림");
            assertThat(notification.getMessage()).isEqualTo("KRW-BTC 현재가 2,671,000원, 최근 5분간 평균가 2,660,000원 대비 0.0% 상승했습니다.");
            assertThat(notification.getPayload()).isEmpty();

            assertThat(notification.getMessageParts())
                    .extracting(NotificationMessagePart::text)
                    .containsExactly(
                            "KRW-BTC",
                            " 현재가 ",
                            "2,671,000원",
                            ", 최근 5분간 평균가 ",
                            "2,660,000원",
                            " 대비 ",
                            "0.0%",
                            " ",
                            "상승",
                            "했습니다."
                    );
        }

        @Test
        @DisplayName("payload는 방어 복사한다")
        void createPriceAlert_should_copy_payload() {
            // given
            Map<String, Object> payload = new HashMap<>();
            payload.put("code", MARKET_CODE);

            // when
            Notification notification = Notification.createPriceAlert(
                    ID,
                    MARKET_CODE,
                    2_671_000D,
                    2_660_000D,
                    5,
                    0.05,
                    payload,
                    CREATED_AT
            );

            payload.put("code", "KRW-ETH");

            // then
            assertThat(notification.getPayload())
                    .containsEntry("code", MARKET_CODE);
        }
    }
}
