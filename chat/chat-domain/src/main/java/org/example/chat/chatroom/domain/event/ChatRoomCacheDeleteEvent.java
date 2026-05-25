package org.example.chat.chatroom.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatroom.domain.port.ChatRoomEventHandler;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

import java.util.Set;

@ToString
@Getter
public class ChatRoomCacheDeleteEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {

    private final String id;
    private final ChatRoomCategory category;
    private final String title;
    private final Set<String> memberids;

    @JsonCreator
    public ChatRoomCacheDeleteEvent(
            @JsonProperty("id") String id,
            @JsonProperty("category") ChatRoomCategory category,
            @JsonProperty("title") String title,
            @JsonProperty("memberIds") Set<String> memberIds) {
        super(KafkaTopic.CHAT_ROOM.getTopicName(), id, id);
        this.id = id;
        this.category = category;
        this.title = title;
        this.memberids = memberIds;
    }

    @Override
    public void handle(ChatRoomEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
