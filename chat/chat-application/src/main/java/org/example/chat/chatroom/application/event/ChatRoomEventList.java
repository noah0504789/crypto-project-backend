package org.example.chat.chatroom.application.event;

import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;

public class ChatRoomEventList extends AbstractOutboxEventList {

    private ChatRoomEventList() {
        super();
    }

    public static ChatRoomEventList of(AbstractOutboxEvent... events) {
        return AbstractOutboxEventList.of(ChatRoomEventList::new, events);
    }
}
