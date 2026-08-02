package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.tx.AfterCommitExecutor;
import org.example.notification.application.port.out.NotificationCachePort;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.application.event.NotificationSaveEvent;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.application.port.in.NotificationEventHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventService implements NotificationEventHandler {

    private final NotificationPersistencePort notificationPersistencePort;
    private final NotificationCachePort notificationCachePort;

    @Override
    @Transactional("notificationMongoTransactionManager")
    public void handle(NotificationSaveEvent event, String txId) {
        Notification notification = event.getPayload().toDomain();
        List<NotificationRecipient> recipients = event.toRecipients();

        notificationPersistencePort.save(notification);
        notificationPersistencePort.saveRecipients(recipients);

        // 생성 시 선적재: master 는 불변이라 여기서 한 번 캐시에 올려두면 이후 조회 콜드 miss 를 없앤다.
        // 커밋 후 실행해 롤백 시 캐시에 유령 항목이 남지 않게 한다(warm-up 실패는 조회 lazy 적재로 흡수).
        AfterCommitExecutor.run(() -> warmUpMasterSafely(notification));

        log.info(
                "Notification saved. txId={}, notificationId={}, recipientCount={}",
                txId,
                notification.getId(),
                recipients.size()
        );
    }

    private void warmUpMasterSafely(Notification notification) {
        try {
            notificationCachePort.warmUpAll(List.of(notification));
        } catch (RuntimeException e) {
            log.warn("[cache] notification master warmUp(create) failed. id={}", notification.getId(), e);
        }
    }
}
