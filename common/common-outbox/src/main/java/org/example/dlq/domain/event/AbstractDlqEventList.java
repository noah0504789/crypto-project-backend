package org.example.dlq.domain.event;

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

    public void publish() {
        this.txId = generateTxId();

        EventUtils.raise(this);

        eventList.clear();
    }

    private String generateTxId() {
        return EventIdUtils.generateTxId();
    }
}
