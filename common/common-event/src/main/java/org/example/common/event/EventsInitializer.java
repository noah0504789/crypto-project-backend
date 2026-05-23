package org.example.common.event;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventsInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationEventPublisher publisher;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        EventUtils.setPublisher(publisher);
    }
}
