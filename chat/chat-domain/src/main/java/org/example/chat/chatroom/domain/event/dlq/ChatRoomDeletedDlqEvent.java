package org.example.chat.chatroom.domain.event.dlq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatroom.domain.event.handler.ChatRoomDlqHandler;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.RecoverableEvent;
import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.outbox.domain.OutboxDomainType;

@Getter
@ToString
public class ChatRoomDeletedDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatRoomDlqHandler> {

    private final String id;
    private final ChatRoomCategory category;

    @JsonCreator
    public ChatRoomDeletedDlqEvent(
            @JsonProperty("id") String id,
            @JsonProperty("category") ChatRoomCategory category,
            @JsonProperty("errorMessage") String errorMessage) {
        super(
                id,
                id,
                KafkaTopic.CHAT_ROOM.getDlqTopicName(),
                OutboxDomainType.CHAT,
                errorMessage
        );
        this.id = id;
        this.category = category;
    }

    @Override
    public void handle(ChatRoomDlqHandler handler) {
        handler.handle(this);
    }
}
