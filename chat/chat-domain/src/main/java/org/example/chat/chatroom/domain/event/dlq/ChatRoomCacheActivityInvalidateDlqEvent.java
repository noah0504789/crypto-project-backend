package org.example.chat.chatroom.domain.event.dlq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatroom.domain.port.ChatRoomDlqHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.RecoverableEvent;
import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.outbox.domain.OutboxDomainType;

@Getter
@ToString
public class ChatRoomCacheActivityInvalidateDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatRoomDlqHandler> {

    private final String id;
    private final String memberId;

    @JsonCreator
    public ChatRoomCacheActivityInvalidateDlqEvent(
            @JsonProperty("id") String id,
            @JsonProperty("memberId") String memberId,
            @JsonProperty("errorMessage") String errorMessage) {
        super(id, id, KafkaTopic.CHAT_ROOM.getDlqTopicName(), OutboxDomainType.CHAT, errorMessage);
        this.id = id;
        this.memberId = memberId;
    }

    @Override
    public void handle(ChatRoomDlqHandler handler) {
        handler.handle(this);
    }
}
