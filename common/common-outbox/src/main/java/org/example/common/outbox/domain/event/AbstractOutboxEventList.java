package org.example.common.outbox.domain.event;

import lombok.Getter;
import org.example.common.event.EventUtils;
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

    /**
     * @deprecated 도메인 객체에서 직접 publish하지 말고,
     * OutboxEventListPublishPort를 통해 발행하도록 점진적으로 교체한다.
     */
    @Deprecated
    public void publish() {
        EventUtils.raise(this);

        eventList.clear();
    }

    private String generateTxId() {
        return EventIdUtils.generateTxId();
    }
}