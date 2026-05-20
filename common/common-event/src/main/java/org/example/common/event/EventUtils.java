package org.example.common.event;

import org.springframework.context.ApplicationEventPublisher;

public class EventUtils {

    private static ApplicationEventPublisher publisher;

    static void setPublisher(ApplicationEventPublisher publisher) {
        EventUtils.publisher = publisher;
    }

    public static void raise(Object event) {
        if (publisher == null) return;

        publisher.publishEvent(event);
    }
}
