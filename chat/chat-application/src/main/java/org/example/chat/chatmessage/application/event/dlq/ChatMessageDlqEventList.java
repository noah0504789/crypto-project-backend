package org.example.chat.chatmessage.application.event.dlq;

import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.dlq.domain.event.AbstractDlqEventList;

public class ChatMessageDlqEventList extends AbstractDlqEventList {

    private ChatMessageDlqEventList() {
        super();
    }

    public static ChatMessageDlqEventList of(AbstractDlqEvent... events) {
        return AbstractDlqEventList.of(ChatMessageDlqEventList::new, events);
    }
}
