package org.example.chat.chatroom.application.event.dlq;

import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.dlq.domain.event.AbstractDlqEventList;

public class ChatRoomDlqEventList extends AbstractDlqEventList {

    private ChatRoomDlqEventList() {
        super();
    }

    public static ChatRoomDlqEventList of(AbstractDlqEvent... events) {
        return AbstractDlqEventList.of(ChatRoomDlqEventList::new, events);
    }
}
