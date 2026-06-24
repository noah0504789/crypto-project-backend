package org.example.market.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PriceAlertSettingClient {

    List<UUID> findReceiverIds(String marketCode, BigDecimal targetChangeRate);
}