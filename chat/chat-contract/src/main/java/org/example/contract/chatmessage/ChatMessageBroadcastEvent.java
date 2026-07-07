package org.example.contract.chatmessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

import java.util.Set;

@ToString
@Getter
public class ChatMessageBroadcastEvent extends AbstractOutboxEvent {

    private final ChatMessagePayload payload;
    private final Set<String> memberIds;
    private final String clientMessageId;

    @JsonCreator
    public ChatMessageBroadcastEvent(
            @JsonProperty("payload") ChatMessagePayload payload,
            @JsonProperty("memberIds") Set<String> memberIds,
            @JsonProperty("clientMessageId") String clientMessageId) {
        super(KafkaTopic.CHAT_MESSAGE_BROADCAST.getTopicName(), payload.id(), payload.roomId());
        this.payload = payload;
        this.memberIds = memberIds;
        this.clientMessageId = clientMessageId;
    }

    @Override
    protected OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.BROADCAST;
    }

    @Override
    protected String getMessageType() {
        return ChatMessageBroadcastEvent.class.getName();
    }
}
