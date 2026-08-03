package org.example.common.dlq.domain.event;

import lombok.Getter;
import org.example.common.event.EventUtils;
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

    /**
     * @deprecated 도메인 객체에서 직접 publish하지 말고,
     * DlqEventListPublishPort를 통해 발행하도록 교체한다.
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