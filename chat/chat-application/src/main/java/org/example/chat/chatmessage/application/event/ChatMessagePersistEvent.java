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

/**
 * 멤버 목록을 싣지 않는다. 정렬 projection 은 방 단위 projector 가 맡으므로 이 이벤트에
 * 방 크기에 비례하는 payload 를 실을 이유가 없다(→ {@code docs/modules/CHAT.md} §5).
 */
@Getter
@ToString
public class ChatMessagePersistEvent extends AbstractOutboxEvent implements HandleableEvent<ChatMessageEventHandler> {

    private final ChatMessagePayload payload;

    @JsonCreator
    public ChatMessagePersistEvent(@JsonProperty("payload") ChatMessagePayload payload) {
        super(KafkaTopic.CHAT_MESSAGE.getTopicName(), payload.id(), payload.roomId());
        this.payload = payload;
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
