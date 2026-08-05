package org.example.notification.application.service.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "notification.price-alert")
public record PriceAlertNotificationProperties(@NotNull Duration maxEventAge) {
}
