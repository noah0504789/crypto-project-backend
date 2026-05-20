package chatmessage.adapter.in;

import config.TestBootApplication;
import org.example.chatmessage.adapter.dto.ChatMessageDlqEventList;
import org.example.chatmessage.adapter.dto.ChatMessageEventList;
import org.example.chatmessage.adapter.in.ChatMessageController;
import org.example.chatmessage.application.port.in.ChatMessageQueryUsecase;
import org.example.chatmessage.domain.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatMessageController.class)
@ContextConfiguration(classes = {TestBootApplication.class, ChatMessageController.class})
class ChatMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatMessageQueryUsecase chatMessageQueryService;

    private final String ROOM_ID = "000000000000000000000001";

    private final String MESSAGE_ID_1 = "100000000000000000000001";
    private final String MESSAGE_ID_2 = "100000000000000000000002";
    private final String MESSAGE_ID_3 = "100000000000000000000003";

    private final String WRITER_ID = "writer-1";

    private final LocalDateTime time1 = LocalDateTime.of(2026, 1, 1, 10, 0);
    private final LocalDateTime time2 = LocalDateTime.of(2026, 1, 1, 11, 0);
    private final LocalDateTime time3 = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Nested
    @DisplayName("GET /chat/room/{roomId}/messages")
    class CursorRecentChatMessagesTest {

        @Test
        @DisplayName("cursor가 없으면 listLatest를 limit+1로 조회하고 hasNext=true를 반환한다")
        void listLatestFirstPageHasNext() throws Exception {
            // given
            given(chatMessageQueryService.listLatest(ROOM_ID, 3))
                    .willReturn(List.of(
                            chatMessage(MESSAGE_ID_3, "message-3", time3),
                            chatMessage(MESSAGE_ID_2, "message-2", time2),
                            chatMessage(MESSAGE_ID_1, "message-1", time1)
                    ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_3))
                    .andExpect(jsonPath("$.items[1].id").value(MESSAGE_ID_2));

            verify(chatMessageQueryService).listLatest(ROOM_ID, 3);
            verify(chatMessageQueryService, never())
                    .listPrev(anyString(), anyString(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("cursor가 없고 결과가 limit 이하이면 hasNext=false를 반환한다")
        void listLatestFirstPageNoNext() throws Exception {
            // given
            given(chatMessageQueryService.listLatest(ROOM_ID, 3))
                    .willReturn(List.of(
                            chatMessage(MESSAGE_ID_2, "message-2", time2),
                            chatMessage(MESSAGE_ID_1, "message-1", time1)
                    ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_2))
                    .andExpect(jsonPath("$.items[1].id").value(MESSAGE_ID_1));

            verify(chatMessageQueryService).listLatest(ROOM_ID, 3);
        }

        @Test
        @DisplayName("cursor가 있으면 listPrev를 limit+1로 조회한다")
        void listPrevWithCursor() throws Exception {
            // given
            long lastCreatedAtMillis = 1_767_224_400_000L;

            given(chatMessageQueryService.listPrev(
                    ROOM_ID,
                    MESSAGE_ID_3,
                    lastCreatedAtMillis,
                    3
            )).willReturn(List.of(
                    chatMessage(MESSAGE_ID_2, "message-2", time2),
                    chatMessage(MESSAGE_ID_1, "message-1", time1)
            ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .param("limit", "2")
                            .param("lastId", MESSAGE_ID_3)
                            .param("lastCreatedAtMillis", String.valueOf(lastCreatedAtMillis)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_2))
                    .andExpect(jsonPath("$.items[1].id").value(MESSAGE_ID_1));

            verify(chatMessageQueryService).listPrev(
                    ROOM_ID,
                    MESSAGE_ID_3,
                    lastCreatedAtMillis,
                    3
            );
            verify(chatMessageQueryService, never()).listLatest(anyString(), anyInt());
        }

        @Test
        @DisplayName("조회 결과가 비어 있으면 items=null, hasNext=false를 반환한다")
        void emptyMessages() throws Exception {
            // given
            given(chatMessageQueryService.listLatest(ROOM_ID, 21))
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").doesNotExist())
                    .andExpect(jsonPath("$.hasNext").value(false));

            verify(chatMessageQueryService).listLatest(ROOM_ID, 21);
        }

        @Test
        @DisplayName("limit을 생략하면 기본값 20을 사용하여 21개를 조회한다")
        void defaultLimit() throws Exception {
            // given
            given(chatMessageQueryService.listLatest(ROOM_ID, 21))
                    .willReturn(List.of(
                            chatMessage(MESSAGE_ID_1, "message-1", time1)
                    ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_1));

            verify(chatMessageQueryService).listLatest(ROOM_ID, 21);
        }
    }

    private ChatMessage chatMessage(String id, String content, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .id(id)
                .roomId(ROOM_ID)
                .writerId(WRITER_ID)
                .content(content)
                .createdAt(createdAt)
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }
}