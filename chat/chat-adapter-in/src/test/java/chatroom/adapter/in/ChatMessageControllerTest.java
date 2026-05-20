//package chatroom.adapter.in;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import config.testcontainer.KafkaTestContainerExtension;
//import config.testcontainer.MongoDBTestContainerExtension;
//import config.testcontainer.RedisTestContainerExtension;
//import org.example.Main;
//import org.example.chatroom.application.port.in.ChatRoomCommandUseCase;
//import org.example.chatroom.application.port.in.ChatRoomQueryUseCase;
//import org.example.chatroom.domain.model.ChatRoom;
//import org.example.chatroom.domain.model.ChatRoomCategory;
//import org.example.chatroom.adapter.dto.ChatRoomRequest;
//import org.example.outbox.adapter.OutboxRepository;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.test.web.servlet.MockMvc;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@AutoConfigureMockMvc
//@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@TestPropertySource(properties = {"spring.cloud.stream.instance-count=2", "spring.cloud.stream.instance-index=0"})
//@ExtendWith({MongoDBTestContainerExtension.class, RedisTestContainerExtension.class, KafkaTestContainerExtension.class})
//public class ChatMessageControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ChatRoomQueryUseCase chatRoomQueryUseCase;
//
//    @Autowired
//    private ChatRoomCommandUseCase chatRoomCommandUseCase;
//
//    @Autowired
//    private MongoTemplate mongoTemplate;
//
//    @Autowired
//    private RedisTemplate redisTemplate;
//
//    @Autowired
//    private OutboxRepository outboxRepository;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private long hostId = 100L;
//    private String title = "test-title";
//    private String description = "test-description";
//    private ChatRoomCategory category = ChatRoomCategory.FREE;
//
//    @AfterEach
//    void clearAll() throws Exception {
//        mongoTemplate.getDb().drop();
//        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
//        outboxRepository.deleteAll();
////        KafkaTestUtils.resetTopic(kafkaTestContainer.getBootstrapServers(), "chatroom-event", 1, (short) 1);
//    }
//
//    @DisplayName("POST /chat/room/ - 성공")
//    @Test
//    void newChatRoom_success() throws Exception {
//        ChatRoomRequest req = new ChatRoomRequest(null, hostId, title, description, category);
//        String json = objectMapper.writeValueAsString(req);
//
//        mockMvc.perform(post("/chat/room")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(json))
//            .andExpect(status().isCreated())
//            .andExpect(header().string("Location", "/home"));
//    }
//
//    @DisplayName("GET /chat/room/{roomId} - 성공")
//    @Test
//    void getChatroom_success() throws Exception {
//        ChatRoom chatRoom = createChatRoom(hostId, title, description, category);
//
//        chatRoomCommandUseCase.save(chatRoom);
//
//        String roomId = chatRoom.getId();
//        mockMvc.perform(get("/chat/room/{roomId}", roomId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(roomId))
//                .andExpect(jsonPath("$.hostId").value(hostId))
//                .andExpect(jsonPath("$.title").value(title))
//                .andExpect(jsonPath("$.description").value(description))
//                .andExpect(jsonPath("$.category").value(category.name()));
//    }
//
//    @DisplayName("GET /chat/room/category/{category} - 성공")
//    @Test
//    void getTopChatRooms_success() throws Exception {
//        ChatRoom chatRoom = createChatRoom(hostId, title, description, category);
//
//        chatRoomCommandUseCase.save(chatRoom);
//
//        mockMvc.perform(get("/chat/room/category/{category}", category.name())
//                        .param("page", "0")
//                        .param("size", "5"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$").isArray())
//                .andExpect(jsonPath("$[0].hostId").value(hostId))
//                .andExpect(jsonPath("$[0].title").value(title))
//                .andExpect(jsonPath("$[0].description").value(description))
//                .andExpect(jsonPath("$[0].category").value(category.name()));
//    }
//
//    @DisplayName("PATCH /chat/room - 성공")
//    @Test
//    void updateChatRoom_success() throws Exception {
//        ChatRoom chatRoom = createChatRoom(hostId, title, description, category);
//        String roomId = chatRoom.getId();
//
//        chatRoomCommandUseCase.save(chatRoom);
//
//        String newTitle = "updated-title";
//        String newDescription = "updated-description";
//
//        ChatRoomRequest req = new ChatRoomRequest(roomId, hostId, newTitle, newDescription, null);
//        String json = objectMapper.writeValueAsString(req);
//
//        mockMvc.perform(patch("/chat/room")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(json))
//                .andExpect(status().isNoContent());
//
//        ChatRoom updated = chatRoomQueryUseCase.find(roomId);
//
//        assertEquals(newTitle, updated.getTitle());
//        assertEquals(newDescription, updated.getDescription());
//    }
//
//    @DisplayName("POST /chat/room/{roomId}/member - 성공")
//    @Test
//    void joinChatRoom_success() throws Exception {
//        ChatRoom chatRoom = createChatRoom(hostId, title, description, category);
//        String roomId = chatRoom.getId();
//
//        chatRoomCommandUseCase.save(chatRoom);
//
//        Long newMemberId = 200L;
//
//        mockMvc.perform(post("/chat/room/{roomId}/member", roomId)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(newMemberId+"")) // TODO: 인증 시, 필요 없음
//                .andExpect(status().isCreated())
//                .andExpect(header().string("Location", "/home"));
//
//        ChatRoom updated = chatRoomQueryUseCase.find(roomId);
//        assertThat(updated.getMemberIds()).containsExactlyInAnyOrder(hostId, newMemberId);
//    }
//
//    @DisplayName("DELETE /chat/room/{roomId}/member - 성공")
//    @Test
//    void leaveChatRoom_success() throws Exception {
//        ChatRoom chatRoom = createChatRoom(hostId, title, description, category);
//        String roomId = chatRoom.getId();
//        Long newMemberId = 200L;
//
//        chatRoomCommandUseCase.save(chatRoom);
//        chatRoomCommandUseCase.join(chatRoom, newMemberId);
//
////        when(cache.getMembers(anyString())).thenReturn(Set.of(hostId, newMemberId));
//
//        mockMvc.perform(delete("/chat/room/{roomId}/member", roomId)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(newMemberId+"")) // TODO: 인증 시, 필요 없음
//                .andExpect(status().isNoContent());
//
//        ChatRoom updated = chatRoomQueryUseCase.find(roomId);
//        assertThat(updated.getMemberIds()).containsExactlyInAnyOrder(hostId);
//    }
//
//    @DisplayName("DELETE /chat/room/{roomId} - 성공")
//    @Test
//    void deleteChatRoom_success() throws Exception {
//        ChatRoom chatRoom = createChatRoom(hostId, title, description, category);
//        String roomId = chatRoom.getId();
//
//        chatRoomCommandUseCase.save(chatRoom);
//
//        mockMvc.perform(delete("/chat/room/{roomId}", roomId))
//                .andExpect(status().isNoContent());
//
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> chatRoomQueryUseCase.find(roomId));
//
//        assertEquals(ex.getMessage(), "채팅방이 존재하지 않습니다");
//    }
//
//    private ChatRoom createChatRoom(long hostId, String title, String description, ChatRoomCategory category) {
//        return new ChatRoom(hostId, title, description, category);
//    }
//}
