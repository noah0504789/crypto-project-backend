package org.example.chatroom.domain.model.event.dlq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatroom.domain.port.ChatRoomDlqHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.event.RecoverableEvent;
import org.example.dlq.AbstractDlqEvent;
import org.example.outbox.domain.OutboxDomainType;
import org.example.outbox.domain.event.AbstractOutboxEvent;

import static org.example.common.enums.KafkaTopic.CHAT_ROOM;

@ToString
@Getter
public class ChatRoomActiveDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatRoomDlqHandler> {

    private final String id;
    private final String memberId;
    private final Long lastMsgSeq;
    private final Long lastMsgMs;

    @JsonCreator
    public ChatRoomActiveDlqEvent(
            @JsonProperty("id") String id,
            @JsonProperty("memberId") String memberId,
            @JsonProperty("lastMsgSeq") Long lastMsgSeq,
            @JsonProperty("lastMsgMs") Long lastMsgMs,
            @JsonProperty("errorMessage") String errorMessage) {
        super(
                id,
                id,
                KafkaTopic.CHAT_ROOM.getDlqTopicName(),
                OutboxDomainType.CHAT,
                errorMessage
        );
        this.id = id;
        this.memberId = memberId;
        this.lastMsgSeq = lastMsgSeq;
        this.lastMsgMs = lastMsgMs;
    }

    @Override
    public void handle(ChatRoomDlqHandler handler) {
        handler.handle(this);
    }
}
