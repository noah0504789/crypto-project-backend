package org.example.notification.application.port.in;

import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;

public interface PriceAlertNotificationCommandUseCase {

    void create(PriceAlertNotificationCreateCommand command);
}