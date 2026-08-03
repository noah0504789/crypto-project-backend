package org.example.common.dlq.domain.event;

import lombok.Getter;
import org.example.common.util.EventIdUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Getter
public class AbstractDlqEventList {

    private final List<AbstractDlqEvent> eventList;
    private final String txId;

    protected AbstractDlqEventList() {
        this.eventList = new ArrayList<>();
        this.txId = generateTxId();
    }

    public static <T extends AbstractDlqEventList> T of(
            Supplier<T> supplier,
            AbstractDlqEvent... events
    ) {
        T eventList = supplier.get();

        Arrays.stream(events).forEach(eventList::addEvent);

        return eventList;
    }

    public void addEvent(AbstractDlqEvent event) {
        this.eventList.add(event);
    }

    private String generateTxId() {
        return EventIdUtils.generateTxId();
    }
}