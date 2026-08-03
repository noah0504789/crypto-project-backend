package org.example.chat.chatmessage.adapter.in.web;

import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.common.test.config.TestBootApplication;
import org.example.chat.chatmessage.application.port.in.ChatMessageQueryUseCase;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatMessageController.class)
@ContextConfiguration(classes = {TestBootApplication.class, ChatMessageController.class})
class ChatMessageControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatMessageQueryUseCase chatMessageQueryService;

    private final String ROOM_ID = "000000000000000000000001";

    private final String MESSAGE_ID_1 = "100000000000000000000001";
    private final String MESSAGE_ID_2 = "100000000000000000000002";
    private final String MESSAGE_ID_3 = "100000000000000000000003";

    private final String WRITER_ID = "writer-1";
    private final String ACTOR_ID = "member-1";

    private final Instant time1 = Instant.parse("2026-01-01T01:00:00Z");
    private final Instant time2 = Instant.parse("2026-01-01T02:00:00Z");
    private final Instant time3 = Instant.parse("2026-01-01T03:00:00Z");

    @Nested
    @DisplayName("GET /chat/room/{roomId}/messages")
    class CursorRecentChatMessagesTest {

        @Test
        @DisplayName("cursor가 없으면 listMessages를 첫 페이지 Query로 limit+1 조회하고 hasNext=true를 반환한다")
        void listMessagesFirstPageHasNext() throws Exception {
            // given
            ListChatMessagesQuery query = ListChatMessagesQuery.firstPage(ROOM_ID, ACTOR_ID, 3);

            given(chatMessageQueryService.listMessages(query))
                    .willReturn(List.of(
                            chatMessage(MESSAGE_ID_3, "message-3", time3),
                            chatMessage(MESSAGE_ID_2, "message-2", time2),
                            chatMessage(MESSAGE_ID_1, "message-1", time1)
                    ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .header("X-User-Id", ACTOR_ID)
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_3))
                    .andExpect(jsonPath("$.items[1].id").value(MESSAGE_ID_2));

            verify(chatMessageQueryService).listMessages(query);
        }

        @Test
        @DisplayName("cursor가 없고 결과가 limit 이하이면 hasNext=false를 반환한다")
        void listMessagesFirstPageNoNext() throws Exception {
            // given
            ListChatMessagesQuery query = ListChatMessagesQuery.firstPage(ROOM_ID, ACTOR_ID, 3);

            given(chatMessageQueryService.listMessages(query))
                    .willReturn(List.of(
                            chatMessage(MESSAGE_ID_2, "message-2", time2),
                            chatMessage(MESSAGE_ID_1, "message-1", time1)
                    ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .header("X-User-Id", ACTOR_ID)
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_2))
                    .andExpect(jsonPath("$.items[1].id").value(MESSAGE_ID_1));

            verify(chatMessageQueryService).listMessages(query);
        }

        @Test
        @DisplayName("cursor가 있으면 listMessages를 이전 페이지 Query로 limit+1 조회한다")
        void listMessagesPrevPageWithCursor() throws Exception {
            // given
            long lastCreatedAtMs = 1_767_224_400_000L;

            ListChatMessagesQuery query = ListChatMessagesQuery.prevPage(
                    ROOM_ID,
                    ACTOR_ID,
                    MESSAGE_ID_3,
                    lastCreatedAtMs,
                    3
            );

            given(chatMessageQueryService.listMessages(query))
                    .willReturn(List.of(
                            chatMessage(MESSAGE_ID_2, "message-2", time2),
                            chatMessage(MESSAGE_ID_1, "message-1", time1)
                    ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .header("X-User-Id", ACTOR_ID)
                            .param("limit", "2")
                            .param("lastMsgId", MESSAGE_ID_3)
                            .param("lastCreatedAtMs", String.valueOf(lastCreatedAtMs)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_2))
                    .andExpect(jsonPath("$.items[1].id").value(MESSAGE_ID_1));

            verify(chatMessageQueryService).listMessages(query);
        }

        @Test
        @DisplayName("조회 결과가 비어 있으면 items=빈 배열, hasNext=false를 반환한다")
        void emptyMessages() throws Exception {
            // given
            ListChatMessagesQuery query = ListChatMessagesQuery.firstPage(ROOM_ID, ACTOR_ID, 21);

            given(chatMessageQueryService.listMessages(query))
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .header("X-User-Id", ACTOR_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.hasNext").value(false));

            verify(chatMessageQueryService).listMessages(query);
        }

        @Test
        @DisplayName("limit을 생략하면 기본값 20을 사용하여 21개를 조회한다")
        void defaultLimit() throws Exception {
            // given
            ListChatMessagesQuery query = ListChatMessagesQuery.firstPage(ROOM_ID, ACTOR_ID, 21);

            given(chatMessageQueryService.listMessages(query))
                    .willReturn(List.of(
                            chatMessage(MESSAGE_ID_1, "message-1", time1)
                    ));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/messages", ROOM_ID)
                            .header("X-User-Id", ACTOR_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID_1));

            verify(chatMessageQueryService).listMessages(query);
        }
    }

    private ChatMessage chatMessage(String id, String content, Instant createdAt) {
        return ChatMessage.rehydrate(id, ROOM_ID, WRITER_ID, content, createdAt);
    }
}