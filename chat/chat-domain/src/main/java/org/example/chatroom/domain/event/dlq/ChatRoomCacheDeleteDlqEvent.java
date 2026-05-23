package org.example.chatroom.domain.event.dlq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatroom.domain.port.ChatRoomDlqHandler;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.RecoverableEvent;
import org.example.dlq.domain.event.AbstractDlqEvent;
import org.example.outbox.domain.OutboxDomainType;

import java.util.Set;

@Getter
@ToString
public class ChatRoomCacheDeleteDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatRoomDlqHandler> {

    private final String id;
    private final ChatRoomCategory category;
    private final String title;
    private final Set<String> memberIds;

    @JsonCreator
    public ChatRoomCacheDeleteDlqEvent(
            @JsonProperty("id") String id,
            @JsonProperty("category") ChatRoomCategory category,
            @JsonProperty("title") String title,
            @JsonProperty("memberIds") Set<String> memberIds,
            @JsonProperty("errorMessage") String errorMessage) {
        super(id, id, KafkaTopic.CHAT_ROOM.getDlqTopicName(), OutboxDomainType.CHAT, errorMessage);
        this.id = id;
        this.category = category;
        this.title = title;
        this.memberIds = memberIds;
    }

    @Override
    public void handle(ChatRoomDlqHandler handler) {
        handler.handle(this);
    }
}
