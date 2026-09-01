package org.example.chat.chatmessage.application.event.dlq;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chat.chatmessage.application.port.in.ChatMessageDlqHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.RecoverableEvent;
import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.contract.chatmessage.ChatMessagePayload;

import java.util.Set;

@Getter
@ToString
public class ChatMessagePersistDlqEvent extends AbstractDlqEvent implements RecoverableEvent<ChatMessageDlqHandler> {

    private final ChatMessagePayload payload;
    private final Set<String> memberIds;

    public ChatMessagePersistDlqEvent(
            @JsonProperty("payload") ChatMessagePayload payload,
            @JsonProperty("errorMessage") String errorMessage
    ) {
        this(payload, Set.of(), errorMessage);
    }

    public ChatMessagePersistDlqEvent(
            @JsonProperty("payload") ChatMessagePayload payload,
            @JsonProperty("memberIds") Set<String> memberIds,
            @JsonProperty("errorMessage") String errorMessage
    ) {
        super(
                payload.id(),
                payload.id(),
                KafkaTopic.CHAT_MESSAGE.getDlqTopicName(),
                OutboxDomainType.CHAT,
                errorMessage
        );
        this.payload = payload;
        this.memberIds = memberIds == null ? Set.of() : memberIds;
    }

    @Override
    public void handle(ChatMessageDlqHandler handler) {
        handler.handle(this);
    }
}
