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
