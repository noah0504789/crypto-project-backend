package org.example.notification.application.event;

import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;

import java.util.Arrays;

public class NotificationEventList extends AbstractOutboxEventList {

    private NotificationEventList() {}

    public static NotificationEventList of(AbstractOutboxEvent... events) {
        NotificationEventList eventList = new NotificationEventList();

        Arrays.stream(events).forEach(eventList::addEvent);

        return eventList;
    }
}
