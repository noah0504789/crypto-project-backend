package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import org.example.common.clock.Clock;
import org.example.notification.application.port.out.PriceAlertRecipientQueryPort;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.domain.model.NotificationType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PriceAlertNotificationEventService {

    private final Clock clock;
    private final PriceAlertRecipientQueryPort priceAlertRecipientQueryPort;

    public void create(PriceAlertNotificationCreateCommand command) {
        Notification notification = createNotification(command);

        LocalDateTime deliveredAt = clock.nowLocalDateTime();

        List<UUID> receiverIds = priceAlertRecipientQueryPort.findReceiverIds(command.code(), command.threshold());

        List<NotificationRecipient> recipients = receiverIds.stream()
                .map(receiverId -> NotificationRecipient.create(notification.getId(), receiverId, deliveredAt))
                .toList();

        notification.save(command.typedPayload(), command.routingKey(), recipients);
    }

    private Notification createNotification(PriceAlertNotificationCreateCommand command) {
        String marketCode = command.code();

        double changeRatePercent = command.changeRate() == null ? 0 : command.changeRate() * 100;

        boolean increased = changeRatePercent >= 0;
        String directionText = increased ? "상승" : "하락";
        String formattedRate = "%.1f%%".formatted(Math.abs(changeRatePercent));

        String title = "가격 알림";
        String message = "%s이 %s 이상 %s했습니다.".formatted(marketCode, formattedRate, directionText);

        List<NotificationMessagePart> messageParts = List.of(
                NotificationMessagePart.bold(marketCode),
                NotificationMessagePart.plain("이 "),
                NotificationMessagePart.bold(formattedRate),
                NotificationMessagePart.plain(" 이상 "),
                NotificationMessagePart.bold(directionText),
                NotificationMessagePart.plain("했습니다.")
        );

        return Notification.create(
                NotificationType.PRICE_ALERT,
                title,
                message,
                messageParts,
                null,
                command.toPayload(),
                clock.nowLocalDateTime()
        );
    }
}
