package org.example.market.application.event;

import org.example.common.outbox.domain.OutboxDispatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCatalogChangedBroadcastEventUnitTest {

    @Test
    @DisplayName("마켓 카탈로그 변경 이벤트는 브로드캐스트 레인으로 발행한다")
    void getDispatchType_shouldReturnBroadcast() {
        MarketCatalogChangedBroadcastEvent event = MarketCatalogChangedBroadcastEvent.of();

        assertThat(event.getDispatchType()).isEqualTo(OutboxDispatchType.BROADCAST);
    }
}
