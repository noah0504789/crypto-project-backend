package org.example.notification.application.port.out;

import java.util.List;
import java.util.UUID;

public interface PriceAlertRecipientQueryPort {

    List<UUID> findReceiverIds(String marketCode, String threshold);
}