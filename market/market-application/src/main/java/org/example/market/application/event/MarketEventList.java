package org.example.market.application.event;

import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;

public class MarketEventList extends AbstractOutboxEventList {

    private MarketEventList() {
        super();
    }

    public static MarketEventList of(AbstractOutboxEvent... events) {
        return AbstractOutboxEventList.of(MarketEventList::new, events);
    }
}
