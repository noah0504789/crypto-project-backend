//package org.example.event.chatroom;
//
//import com.fasterxml.jackson.annotation.JsonCreator;
//import com.fasterxml.jackson.annotation.JsonProperty;
//import lombok.Getter;
//import lombok.ToString;
//import org.example.chatroom.application.port.in.ChatRoomEventHandler;
//import org.example.chatroom.domain.model.ChatRoom;
//import org.example.outbox.domain.event.AbstractOutboxEvent;
//import org.example.event.HandleableEvent;
//import org.example.outbox.domain.OutboxType;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//@ToString
//@Getter
//public class ChatRoomListStoredEvent extends AbstractOutboxEvent implements HandleableEvent<ChatRoomEventHandler> {
//
//    private List<ChatRoom> rooms;
//    private Map<String, Set<String>> members;
//    private Map<String, Double> scores;
//
//    @JsonCreator
//    public ChatRoomListStoredEvent(@JsonProperty("rooms") List<ChatRoom> rooms, @JsonProperty("members") Map<String, Set<String>> members, @JsonProperty("scores") Map<String, Double> scores) {
//        super(ChatRoom.class.getName(), OutboxType.GENERAL);
//        this.rooms = rooms;
//        this.members = members;
//        this.scores = scores;
//    }
//
//    @Override
//    public void handle(ChatRoomEventHandler handler) {
//        handler.handleListStored(this);
//    }
//}
