package org.example.chatroom.domain.model.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatroom.domain.port.ChatRoomEventHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.outbox.domain.event.AbstractOutboxEvent;

import java.util.Map;

import static org.example.common.enums.KafkaTopic.CHAT_ROOM;

@ToString
@Getter
public class ChatRoomUpdatedEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {

    private final String id;
    private final Map<String, Object> updated;

    @JsonCreator
    public ChatRoomUpdatedEvent(@JsonProperty("id") String id, @JsonProperty("updated") Map<String, Object> updated) {
        super(CHAT_ROOM.getTopicName(), id, id);
        this.id = id;
        this.updated = updated;
    }

    @Override
    public void handle(ChatRoomEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
