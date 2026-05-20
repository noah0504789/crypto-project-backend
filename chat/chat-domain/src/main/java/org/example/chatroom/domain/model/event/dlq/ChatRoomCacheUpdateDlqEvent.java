package org.example.chatroom.domain.model.event.dlq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatroom.domain.port.ChatRoomDlqHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.RecoverableEvent;
import org.example.dlq.AbstractDlqEvent;
import org.example.outbox.domain.OutboxDomainType;

@Getter
@ToString
public class ChatRoomCacheUpdateDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatRoomDlqHandler> {

    private final String id;
    private final String oldTitle;

    @JsonCreator
    public ChatRoomCacheUpdateDlqEvent(
            @JsonProperty("id") String id,
            @JsonProperty("oldTitle") String oldTitle,
            @JsonProperty("errorMessage") String errorMessage) {
        super(id, id, KafkaTopic.CHAT_ROOM.getDlqTopicName(), OutboxDomainType.CHAT, errorMessage);
        this.id = id;
        this.oldTitle = oldTitle;
    }

    @Override
    public void handle(ChatRoomDlqHandler handler) {
        handler.handle(this);
    }
}
