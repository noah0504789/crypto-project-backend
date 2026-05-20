package org.example.outbox.domain.event;

import lombok.Getter;
import org.example.common.event.EventUtils;
import org.example.common.util.EventIdUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class AbstractOutboxEventList {

    private final List<AbstractOutboxEvent> eventList;
    private String txId;

    public AbstractOutboxEventList() {
        this.eventList = new ArrayList<>();
    }

    public AbstractOutboxEventList addEvent(AbstractOutboxEvent event) {
        this.eventList.add(event);

        return this;
    }

    public void publish() {
        this.txId = generateTxId();

        EventUtils.raise(this);

        eventList.clear();
    }

    private String generateTxId() {
        return EventIdUtils.generateTxId();
    }
}
