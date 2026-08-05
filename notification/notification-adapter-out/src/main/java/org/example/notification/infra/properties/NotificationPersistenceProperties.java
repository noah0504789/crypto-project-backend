package org.example.notification.infra.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 수신자 fan-out bulk write 한 묶음 크기. 크면 왕복이 줄지만 실패 시 재시도 단위가 커진다. */
@Validated
@ConfigurationProperties(prefix = "notification.persistence")
public record NotificationPersistenceProperties(@Positive Integer batchSize) {
}
