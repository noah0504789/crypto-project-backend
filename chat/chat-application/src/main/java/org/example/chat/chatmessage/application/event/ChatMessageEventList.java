package org.example.chat.chatmessage.application.event;

import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;

public class ChatMessageEventList extends AbstractOutboxEventList {

    private ChatMessageEventList() {
        super();
    }

    public static ChatMessageEventList of(AbstractOutboxEvent... events) {
        return AbstractOutboxEventList.of(ChatMessageEventList::new, events);
    }
}
