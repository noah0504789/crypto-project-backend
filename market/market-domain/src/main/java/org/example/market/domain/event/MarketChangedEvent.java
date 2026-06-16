package org.example.market.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.market.domain.port.MarketEventHandler;

@Getter
@ToString
public class MarketChangedEvent extends AbstractOutboxEvent implements HandleableEvent<MarketEventHandler> {

    @JsonCreator
    public MarketChangedEvent() {
        super(KafkaTopic.MARKET_CHANGED.getTopicName(), "MARKET_LIST", "MARKET_LIST");
    }

    public static MarketChangedEvent of() {
        return new MarketChangedEvent();
    }

    @Override
    protected OutboxDomainType getDomainType() {
        return OutboxDomainType.MARKET;
    }

    @Override
    protected OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.BROADCAST;
    }

    @Override
    public void handle(MarketEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
