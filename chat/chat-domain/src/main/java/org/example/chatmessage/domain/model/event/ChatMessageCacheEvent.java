//package org.example.chatmessage.domain.model.event;
//
//import com.fasterxml.jackson.annotation.JsonCreator;
//import com.fasterxml.jackson.annotation.JsonProperty;
//import lombok.Getter;
//import lombok.ToString;
//import org.example.chatmessage.domain.model.ChatMessage;
//import org.example.outbox.domain.event.AbstractOutboxEvent;
//
//import static org.example.common.enums.KafkaTopic.CHAT_MESSAGE;
//
//@Getter
//@ToString
//public class ChatMessageCacheEvent extends AbstractOutboxEvent {
//
//    private ChatMessage domain;
//    private boolean isDlq;
//
//    @JsonCreator
//    public ChatMessageCacheEvent(@JsonProperty("domain") ChatMessage domain) {
//        super(CHAT_MESSAGE.getTopicName(), domain.getId());
//        this.domain = domain;
//    }
//
//    public ChatMessageCacheEvent(@JsonProperty("domain") ChatMessage domain, boolean isDlq) {
//        super(isDlq ? CHAT_MESSAGE.getDlqTopicName() : CHAT_MESSAGE.getTopicName(), domain.getId());
//        this.domain = domain;
//    }
//}
