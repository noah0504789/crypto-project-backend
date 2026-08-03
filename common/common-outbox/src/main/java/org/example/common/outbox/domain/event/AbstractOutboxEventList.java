package org.example.common.outbox.domain.event;

import lombok.Getter;
import org.example.common.util.EventIdUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Getter
public abstract class AbstractOutboxEventList {

    private final List<AbstractOutboxEvent> eventList;
    private final String txId;

    protected AbstractOutboxEventList() {
        this.eventList = new ArrayList<>();
        this.txId = generateTxId();
    }

    public static <T extends AbstractOutboxEventList> T of(
            Supplier<T> supplier,
            AbstractOutboxEvent... events
    ) {
        T eventList = supplier.get();

        Arrays.stream(events).forEach(eventList::addEvent);

        return eventList;
    }

    public void addEvent(AbstractOutboxEvent event) {
        this.eventList.add(event);
    }

    private String generateTxId() {
        return EventIdUtils.generateTxId();
    }
}