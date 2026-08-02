package org.example.chat.chatmessage.application.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatmessage.application.port.in.ChatMessageEventHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.contract.chatmessage.ChatMessagePayload;

import java.util.Set;

@Getter
@ToString
public class ChatMessagePersistEvent extends AbstractOutboxEvent implements HandleableEvent<ChatMessageEventHandler> {

    private final ChatMessagePayload payload;
    private final Set<String> memberIds;

    @JsonCreator
    public ChatMessagePersistEvent(
            @JsonProperty("payload") ChatMessagePayload payload,
            @JsonProperty("memberIds") Set<String> memberIds
    ) {
        super(KafkaTopic.CHAT_MESSAGE.getTopicName(), payload.id(), payload.roomId());
        this.payload = payload;
        this.memberIds = memberIds;
    }

    @Override
    public void handle(ChatMessageEventHandler handler, String txId) {
        handler.handle(this, txId);
    }

    @Override
    public OutboxDomainType getDomainType() {
        return OutboxDomainType.CHAT;
    }
}
