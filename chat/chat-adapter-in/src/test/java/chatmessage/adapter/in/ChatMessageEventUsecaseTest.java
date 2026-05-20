//package chatmessage.adapter.in;
//
//import org.bson.types.ObjectId;
//import org.example.chatmessage.application.service.ChatMessageEventService;
//import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
//import org.example.chatmessage.domain.model.ChatMessage;
//import org.example.chatmessage.domain.model.event.ChatMessagePersistEvent;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.doThrow;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//public class ChatMessageEventUsecaseTest {
//
////    @Mock private ChatMessageCachePort cache;
////    @Mock private ChatMessageBroadcastService chatMessageBroadcastService;
//
//    @Mock private ChatMessagePersistencePort persistence;
//    @InjectMocks private ChatMessageEventService sut;
//
//    private String messageId = new ObjectId().toHexString();
//
//    // TODO: handleCached
//
//    @Test
//    void handlePersisted_success() {
//        ChatMessagePersistEvent event = mock(ChatMessagePersistEvent.class);
//        ChatMessage domain = mock(ChatMessage.class);
//
//        when(event.getPayload()).thenReturn(domain);
////        when(domain.getId()).thenReturn(messageId);
//
////        sut.handle(event);
//
//        verify(persistence).create(eq(domain));
//
////        verify(domain).broadcast();
//    }
//
////    @Test
////    void handlePersisted_cache_failed() {
////        ChatMessagePersistEvent event = mock(ChatMessagePersistEvent.class);
////        ChatMessage domain = mock(ChatMessage.class);
////
////        when(event.getDomain()).thenReturn(domain);
////        doThrow(new RuntimeException("mysql 동작 안함")).when(persistence).create(eq(domain));
////
////        assertThrows(RuntimeException.class, () -> sut.handlePersisted(event));
////
//////        verify(persistence, never()).create(any());
//////        verify(chatMessageBroadcastService, never()).save(any());
//////        verify(domain, never()).broadcast();
////    }
//}
