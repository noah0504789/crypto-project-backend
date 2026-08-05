package org.example.market.application.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.market.application.port.in.MarketEventHandler;

@Getter
@ToString
public class MarketCatalogChangedBroadcastEvent extends AbstractOutboxEvent implements HandleableEvent<MarketEventHandler> {

    @JsonCreator
    public MarketCatalogChangedBroadcastEvent() {
        super(KafkaTopic.MARKET_CATALOG_CHANGED_BROADCAST.getTopicName(), "MARKET_LIST", "MARKET_LIST");
    }

    @Override
    public OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.BROADCAST;
    }

    @Override
    public OutboxDomainType getDomainType() {
        return OutboxDomainType.MARKET;
    }

    @Override
    public void handle(MarketEventHandler handler, String txId) {
        handler.handle(this, txId);
    }

    public static MarketCatalogChangedBroadcastEvent of() {
        return new MarketCatalogChangedBroadcastEvent();
    }
}
