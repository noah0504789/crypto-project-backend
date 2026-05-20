package org.example.chatroom.domain.model.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatroom.domain.port.ChatRoomEventHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.outbox.domain.event.AbstractOutboxEvent;

@ToString
@Getter
public class ChatRoomCacheUpdateEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {

    private final String id;
    private final String oldTitle;

    @JsonCreator
    public ChatRoomCacheUpdateEvent(
            @JsonProperty("id") String id,
            @JsonProperty("oldTitle") String oldTitle) {
        super(KafkaTopic.CHAT_ROOM.getTopicName(), id, id);
        this.id = id;
        this.oldTitle = oldTitle;
    }

    @Override
    public void handle(ChatRoomEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
