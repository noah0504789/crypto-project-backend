package org.example.common.dlq.domain.event;

import lombok.Getter;
import org.example.common.event.EventUtils;
import org.example.common.util.EventIdUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AbstractDlqEventList {

    private final List<AbstractDlqEvent> eventList;
    private String txId;

    public AbstractDlqEventList() {
        this.eventList = new ArrayList<>();
    }

    public void addEvent(AbstractDlqEvent event) {
        this.eventList.add(event);
    }

    public void assignTxId() {
        this.txId = generateTxId();
    }

    /**
     * @deprecated 도메인 객체에서 직접 publish하지 말고,
     * DlqEventListPublishPort를 통해 발행하도록 교체한다.
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