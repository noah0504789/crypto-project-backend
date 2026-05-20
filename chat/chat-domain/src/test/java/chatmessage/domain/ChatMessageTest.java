//package chatmessage.domain;
//
//import org.bson.types.ObjectId;
//import org.example.chatmessage.domain.model.ChatMessage;
//import org.example.chatmessage.domain.model.event.ChatMessageBroadcastEvent;
//import org.example.outbox.domain.event.AbstractOutboxEvent;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//@ExtendWith(SpringExtension.class)
//public class ChatMessageTest {
//
//    private String roomId = new ObjectId().toHexString();
//    private long writerId = 100L;
//    private String content = "test-message";
//
//
//
//    @Test
//    void broadcast_success() {
//        ChatMessage sut = createChatMessage(roomId, writerId, content);
//
//        sut.broadcast();
//
//        AbstractOutboxEvent event = sut.getEventList().getEventList().get(0);
//        assertTrue(event instanceof ChatMessageBroadcastEvent);
//
//        ChatMessageBroadcastEvent broadcastEvent = (ChatMessageBroadcastEvent) event;
//        assertEquals(broadcastEvent.getDomain(), sut);
//    }
//
//    private ChatMessage createChatMessage(String roomId, Long writerId, String content) {
//        return new ChatMessage(roomId, writerId, content);
//    }
//}
