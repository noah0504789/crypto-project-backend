package org.example.chat.chatroom.application.event.dlq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatroom.application.port.in.ChatRoomDlqHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.RecoverableEvent;
import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.outbox.domain.OutboxDomainType;

@Getter
@ToString
public class ChatRoomCacheSaveDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatRoomDlqHandler> {

    private final String id;

    @JsonCreator
    public ChatRoomCacheSaveDlqEvent(
            @JsonProperty("id") String id,
            @JsonProperty("errorMessage") String errorMessage) {
        super(id, id, KafkaTopic.CHAT_ROOM.getDlqTopicName(), OutboxDomainType.CHAT, errorMessage);
        this.id = id;
    }

    @Override
    public void handle(ChatRoomDlqHandler handler) {
        handler.handle(this);
    }
}
