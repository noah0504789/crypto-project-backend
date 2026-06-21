package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.domain.event.NotificationSaveEvent;
import org.example.notification.domain.event.NotificationPayload;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.domain.port.NotificationEventHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventService implements NotificationEventHandler {

    private final NotificationPersistencePort notificationPersistencePort;

    @Override
    @Transactional("notificationMongoTransactionManager")
    public void handle(NotificationSaveEvent event, String txId) {
        Notification notification = event.getPayload().toDomain();
        List<NotificationRecipient> recipients = event.toRecipients();

        notificationPersistencePort.save(notification);
        notificationPersistencePort.saveRecipients(recipients);

        log.info(
                "Notification saved. txId={}, notificationId={}, recipientCount={}",
                txId,
                notification.getId(),
                recipients.size()
        );
    }
}