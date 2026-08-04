package org.example.notification.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 수신자 fan-out bulk write 한 묶음 크기. 크면 왕복이 줄지만 실패 시 재시도 단위가 커진다. */
@ConfigurationProperties(prefix = "notification.persistence")
public record NotificationPersistenceProperties(@DefaultValue("1000") int batchSize) {
}
