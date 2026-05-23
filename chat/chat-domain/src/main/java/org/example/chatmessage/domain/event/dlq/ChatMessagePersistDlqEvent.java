package org.example.chatmessage.domain.event.dlq;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatmessage.domain.event.payload.ChatMessagePayload;
import org.example.chatmessage.domain.port.ChatMessageDlqHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.RecoverableEvent;
import org.example.dlq.domain.event.AbstractDlqEvent;
import org.example.outbox.domain.OutboxDomainType;

@Getter
@ToString
public class ChatMessagePersistDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatMessageDlqHandler> {

    private final ChatMessagePayload payload;

    public ChatMessagePersistDlqEvent(
            @JsonProperty("payload") ChatMessagePayload payload,
            @JsonProperty("errorMessage") String errorMessage) {
        super(
                payload.id(),
                payload.id(),
                KafkaTopic.CHAT_MESSAGE.getDlqTopicName(),
                OutboxDomainType.CHAT,
                errorMessage
        );
        this.payload = payload;
    }

    @Override
    public void handle(ChatMessageDlqHandler handler) {
        handler.handle(this);
    }
}
