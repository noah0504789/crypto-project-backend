package org.example.market.application.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.market.application.port.in.MarketEventHandler;

@Getter
@ToString
public class MarketCatalogChangedEvent extends AbstractOutboxEvent implements HandleableEvent<MarketEventHandler> {

    @JsonCreator
    public MarketCatalogChangedEvent() {
        super(KafkaTopic.MARKET_CHANGED_BROADCAST.getTopicName(), "MARKET_LIST", "MARKET_LIST");
    }

    @Override
    protected OutboxDomainType getDomainType() {
        return OutboxDomainType.MARKET;
    }

    @Override
    public void handle(MarketEventHandler handler, String txId) {
        handler.handle(this, txId);
    }

    public static MarketCatalogChangedEvent of() {
        return new MarketCatalogChangedEvent();
    }
}
