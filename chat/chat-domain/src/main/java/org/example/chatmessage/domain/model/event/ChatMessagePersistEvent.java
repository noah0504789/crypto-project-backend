package org.example.chatmessage.domain.model.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.chatmessage.adapter.dto.ChatMessagePayload;
import org.example.chatmessage.domain.port.ChatMessageEventHandler;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.outbox.domain.event.AbstractOutboxEvent;

import java.util.Set;

import static org.example.common.enums.KafkaTopic.CHAT_MESSAGE;

@Getter
@ToString
public class ChatMessagePersistEvent extends AbstractOutboxEvent implements HandleableEvent<ChatMessageEventHandler> {

    private final ChatMessagePayload payload;
    private final Set<String> memberIds;

    @JsonCreator
    public ChatMessagePersistEvent(
            @JsonProperty("payload") ChatMessagePayload payload,
            @JsonProperty("memberIds") Set<String> memberIds) {
        super(KafkaTopic.CHAT_MESSAGE.getTopicName(), payload.id(), payload.roomId());
        this.payload = payload;
        this.memberIds = memberIds;
    }

    @Override
    public void handle(ChatMessageEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}
