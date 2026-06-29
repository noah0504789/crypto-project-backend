package org.example.common.outbox.domain.event;

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

    public void assignTxId() {
        this.txId = generateTxId();
    }

    /**
     * @deprecated 도메인 객체에서 직접 publish하지 말고,
     * OutboxEventListPublishPort를 통해 발행하도록 점진적으로 교체한다.
     */
    @Deprecated
    public void publish() {
        assignTxId();

        EventUtils.raise(this);

        eventList.clear();
    }

    private String generateTxId() {
        return EventIdUtils.generateTxId();
    }
}