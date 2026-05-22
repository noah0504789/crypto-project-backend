package org.example.chatroom.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatroom.domain.port.ChatRoomEventHandler;
import org.example.common.event.HandleableEvent;
import org.example.outbox.domain.event.AbstractOutboxEvent;

import static org.example.common.enums.KafkaTopic.CHAT_ROOM;

@ToString
@Getter
public class ChatRoomActiveEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {

    private final String id;
    private final String memberId;
    private final Long lastMsgSeq;
    private final Long lastMsgMs;

    @JsonCreator
    public ChatRoomActiveEvent(
            @JsonProperty("id") String id,
            @JsonProperty("memberId") String memberId,
            @JsonProperty("lastMsgSeq") Long lastMsgSeq,
            @JsonProperty("lastMsgMs") Long lastMsgMs) {
        super(CHAT_ROOM.getTopicName(), id, id);
        this.id = id;
        this.memberId = memberId;
        this.lastMsgSeq = lastMsgSeq;
        this.lastMsgMs = lastMsgMs;
    }

    @Override
    public void handle(ChatRoomEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
