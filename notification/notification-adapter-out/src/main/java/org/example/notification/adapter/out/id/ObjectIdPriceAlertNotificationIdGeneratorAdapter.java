package org.example.notification.adapter.out.id;

import lombok.RequiredArgsConstructor;
import org.example.common.id.ObjectIdGenerator;
import org.springframework.stereotype.Component;
import org.example.notification.application.port.out.PriceAlertNotificationIdGeneratorPort;

@Component
@RequiredArgsConstructor
public class ObjectIdPriceAlertNotificationIdGeneratorAdapter implements PriceAlertNotificationIdGeneratorPort {

    private final ObjectIdGenerator objectIdGenerator;

    @Override
    public String generate() {
        return objectIdGenerator.generate();
    }
}
