package org.example.notification.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.time.ServiceTimeConverter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Notification {

    private String id;
    private NotificationType type;
    private String title;
    private String message;
    private List<NotificationMessagePart> messageParts;
    private String link;
    private Map<String, Object> payload;

    private boolean deleted;
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    public static Notification createPriceAlert(
            String id,
            String marketCode,
            Double price,
            Double averagePrice,
            Integer averageInterval,
            Double changeRate,
            Map<String, Object> payload,
            LocalDateTime createdAt
    ) {
        double changeRatePercent = changeRate == null ? 0 : changeRate * 100;

        boolean increased = changeRatePercent >= 0;
        String directionText = increased ? "상승" : "하락";
        String formattedRate = "%.1f%%".formatted(Math.abs(changeRatePercent));
        String formattedPrice = "%,.0f".formatted(price);
        String formattedAveragePrice = "%,.0f".formatted(averagePrice);
        String averageIntervalText = averageInterval + "분간";

        String title = "가격 알림";
        String message = "%s 현재가 %s원, 최근 %s 평균가 %s원 대비 %s %s했습니다."
                .formatted(marketCode, formattedPrice, averageIntervalText, formattedAveragePrice, formattedRate, directionText);

        List<NotificationMessagePart> messageParts = List.of(
                NotificationMessagePart.bold(marketCode),
                NotificationMessagePart.plain(" 현재가 "),
                NotificationMessagePart.bold(formattedPrice + "원"),
                NotificationMessagePart.plain(", 최근 " + averageIntervalText + " 평균가 "),
                NotificationMessagePart.bold(formattedAveragePrice + "원"),
                NotificationMessagePart.plain(" 대비 "),
                NotificationMessagePart.bold(formattedRate),
                NotificationMessagePart.plain(" "),
                NotificationMessagePart.bold(directionText),
                NotificationMessagePart.plain("했습니다.")
        );

        return Notification.builder()
                .id(id)
                .type(NotificationType.PRICE_ALERT)
                .title(title)
                .message(message)
                .messageParts(messageParts)
                .link(null)
                .payload(payload == null ? Map.of() : Map.copyOf(payload))
                .deleted(false)
                .deletedAt(null)
                .createdAt(createdAt)
                .build();
    }

    public static Notification rehydrate(
            String id,
            NotificationType type,
            String title,
            String message,
            List<NotificationMessagePart> messageParts,
            String link,
            Map<String, Object> payload,
            boolean deleted,
            LocalDateTime deletedAt,
            LocalDateTime createdAt
    ) {
        return Notification.builder()
                .id(id)
                .type(type)
                .title(title)
                .message(message)
                .messageParts(messageParts == null ? List.of() : List.copyOf(messageParts))
                .link(link)
                .payload(payload == null ? Map.of() : Map.copyOf(payload))
                .deleted(deleted)
                .deletedAt(deletedAt)
                .createdAt(createdAt)
                .build();
    }

    public long getCreatedAtMs() {
        return ServiceTimeConverter.toEpochMillis(createdAt);
    }
}
