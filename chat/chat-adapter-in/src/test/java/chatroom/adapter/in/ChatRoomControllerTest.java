package chatroom.adapter.in;

import org.example.chat.chatroom.application.dto.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.query.MyChatRoomSummary;
import org.example.common.test.config.TestBootApplication;
import org.bson.types.ObjectId;
import org.example.chat.chatroom.application.dto.ChatRoomCreateRequest;
import org.example.chat.chatroom.adapter.in.web.ChatRoomController;
import org.example.chat.chatroom.application.port.in.ChatRoomCommandUseCase;
import org.example.chat.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.exception.GlobalExceptionHandler;
import org.example.common.validation.NotBlankIfPresentValidator;
import org.example.chat.chatroom.application.validation.UniqueChatRoomTitleValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatRoomController.class)
@ContextConfiguration(classes = {
        TestBootApplication.class,
        ChatRoomController.class,
        UniqueChatRoomTitleValidator.class,
        NotBlankIfPresentValidator.class,
        GlobalExceptionHandler.class
})
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatRoomCommandUseCase chatRoomCommandUseCase;

    @MockitoBean
    private ChatRoomQueryUseCase chatRoomQueryUseCase;

    private final String USER_ID = "user-1";
    private final String HOST_ID = "host-1";

    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    private final String roomId1 = new ObjectId("000000000000000000000001").toHexString();
    private final String roomId2 = new ObjectId("000000000000000000000002").toHexString();
    private final String roomId3 = new ObjectId("000000000000000000000003").toHexString();

    @Nested
    @DisplayName("GET /chat/rooms/popular")
    class PopularChatRoomsTest {

        @Test
        @DisplayName("cursor가 없으면 listMostPopular를 limit+1로 조회하고 hasNext=true를 반환한다")
        void popularChatRoomsFirstPageHasNext() throws Exception {
            // given
            given(chatRoomQueryUseCase.listMostPopular(category, 3))
                    .willReturn(List.of(
                            chatRoom(roomId1, "방1", 30L),
                            chatRoom(roomId2, "방2", 20L),
                            chatRoom(roomId3, "방3", 10L)
                    ));

            // when & then
            mockMvc.perform(get("/chat/rooms/popular")
                            .param("category", category.name())
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(roomId1))
                    .andExpect(jsonPath("$.items[1].id").value(roomId2));

            verify(chatRoomQueryUseCase).listMostPopular(category, 3);
            verify(chatRoomQueryUseCase, never())
                    .listNextPopular(any(), anyString(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("cursor가 있으면 listNextPopular를 limit+1로 조회한다")
        void popularChatRoomsNextPage() throws Exception {
            // given
            given(chatRoomQueryUseCase.listNextPopular(category, roomId3, 10L, 3))
                    .willReturn(List.of(
                            chatRoom(roomId2, "방2", 20L),
                            chatRoom(roomId1, "방1", 10L)
                    ));

            // when & then
            mockMvc.perform(get("/chat/rooms/popular")
                            .param("category", category.name())
                            .param("limit", "2")
                            .param("lastId", roomId3)
                            .param("lastPopularity", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(roomId2))
                    .andExpect(jsonPath("$.items[1].id").value(roomId1));

            verify(chatRoomQueryUseCase).listNextPopular(category, roomId3, 10L, 3);
            verify(chatRoomQueryUseCase, never()).listMostPopular(any(), anyInt());
        }

        @Test
        @DisplayName("조회 결과가 비어 있으면 items=null, hasNext=false를 반환한다")
        void popularChatRoomsEmpty() throws Exception {
            // given
            given(chatRoomQueryUseCase.listMostPopular(category, 11))
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(get("/chat/rooms/popular")
                            .param("category", category.name()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").doesNotExist())
                    .andExpect(jsonPath("$.hasNext").value(false));

            verify(chatRoomQueryUseCase).listMostPopular(category, 11);
        }

        @Test
        @DisplayName("category가 없으면 400을 반환한다")
        void popularChatRoomsWithoutCategory() throws Exception {
            mockMvc.perform(get("/chat/rooms/popular"))
                    .andExpect(status().isBadRequest());

            verify(chatRoomQueryUseCase, never()).listMostPopular(any(), anyInt());
            verify(chatRoomQueryUseCase, never()).listNextPopular(any(), anyString(), anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("GET /chat/rooms/me")
    class MyChatRoomsTest {

        @Test
        @DisplayName("cursor가 없으면 listLatestActive를 limit+1로 조회하고 hasNext=true를 반환한다")
        void myChatRoomsFirstPageHasNext() throws Exception {
            // given
            given(chatRoomQueryUseCase.listLatestActive(USER_ID, 3))
                    .willReturn(List.of(
                            myChatRoomSummary(roomId1, "내 방1", 0L),
                            myChatRoomSummary(roomId2, "내 방2", 0L),
                            myChatRoomSummary(roomId3, "내 방3", 0L)
                    ));

            // when & then
            mockMvc.perform(get("/chat/rooms/me")
                            .header("X-User-Id", USER_ID)
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].id").value(roomId1))
                    .andExpect(jsonPath("$.items[1].id").value(roomId2));

            verify(chatRoomQueryUseCase).listLatestActive(USER_ID, 3);
            verify(chatRoomQueryUseCase, never())
                    .listActiveBefore(anyString(), anyString(), anyBoolean(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("cursor가 있으면 listActiveBefore를 limit+1로 조회한다")
        void myChatRoomsNextPage() throws Exception {
            // given
            Instant lastMsgCreatedAt = Instant.parse("2026-01-01T03:00:00Z");

            given(chatRoomQueryUseCase.listActiveBefore(
                    USER_ID,
                    roomId3,
                    true,
                    lastMsgCreatedAt.toEpochMilli(),
                    3
            )).willReturn(List.of(
                    myChatRoomSummary(roomId2, "내 방2", 0L)
            ));

            // when & then
            mockMvc.perform(get("/chat/rooms/me")
                            .header("X-User-Id", USER_ID)
                            .param("limit", "2")
                            .param("lastId", roomId3)
                            .param("lastUnreadFlag", "true")
                            .param("lastMsgCreatedAt", lastMsgCreatedAt.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].id").value(roomId2));

            verify(chatRoomQueryUseCase).listActiveBefore(
                    USER_ID,
                    roomId3,
                    true,
                    lastMsgCreatedAt.toEpochMilli(),
                    3
            );
            verify(chatRoomQueryUseCase, never()).listLatestActive(anyString(), anyInt());
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void myChatRoomsWithoutUserHeader() throws Exception {
            mockMvc.perform(get("/chat/rooms/me"))
                    .andExpect(status().isBadRequest());

            verify(chatRoomQueryUseCase, never()).listLatestActive(anyString(), anyInt());
        }

        @Test
        @DisplayName("조회 결과가 비어 있으면 items=null, hasNext=false를 반환한다")
        void myChatRoomsEmpty() throws Exception {
            // given
            given(chatRoomQueryUseCase.listLatestActive(USER_ID, 11))
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(get("/chat/rooms/me")
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").doesNotExist())
                    .andExpect(jsonPath("$.hasNext").value(false));

            verify(chatRoomQueryUseCase).listLatestActive(USER_ID, 11);
        }
    }

    @Nested
    @DisplayName("GET /chat/room/{roomId}")
    class FindRoomTest {

        @Test
        @DisplayName("채팅방 단건을 조회한다")
        void chatRoom_() throws Exception {
            // given
            given(chatRoomQueryUseCase.findById(roomId1))
                    .willReturn(chatRoom(roomId1, "방1", 0L));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}", roomId1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(roomId1))
                    .andExpect(jsonPath("$.title").value("방1"));

            verify(chatRoomQueryUseCase).findById(roomId1);
        }

        @Test
        @DisplayName("내 채팅방 정보를 조회한다")
        void myChatRoom() throws Exception {
            // given
            given(chatRoomQueryUseCase.findActive(roomId1, USER_ID))
                    .willReturn(myChatRoomSummary(roomId1, "내 방1", 0L));

            // when & then
            mockMvc.perform(get("/chat/room/{roomId}/me", roomId1)
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(roomId1))
                    .andExpect(jsonPath("$.title").value("내 방1"));

            verify(chatRoomQueryUseCase).findActive(roomId1, USER_ID);
        }

        @Test
        @DisplayName("내 채팅방 조회 시 X-User-Id 헤더가 없으면 400을 반환한다")
        void myChatRoomWithoutUserHeader() throws Exception {
            mockMvc.perform(get("/chat/room/{roomId}/me", roomId1))
                    .andExpect(status().isBadRequest());

            verify(chatRoomQueryUseCase, never()).findActive(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("POST /chat/room/{roomId}/members")
    class JoinTest {

        @Test
        @DisplayName("신규 멤버면 201 Created를 반환한다")
        void joinNewMember() throws Exception {
            // given
            given(chatRoomCommandUseCase.join(roomId1, USER_ID))
                    .willReturn(true);

            // when & then
            mockMvc.perform(post("/chat/room/{roomId}/members", roomId1)
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(
                            "Location",
                            String.format("/chat/room/%s/member/%s", roomId1, USER_ID)
                    ));

            verify(chatRoomCommandUseCase).join(roomId1, USER_ID);
        }

        @Test
        @DisplayName("이미 참여한 멤버면 204 No Content를 반환한다")
        void joinExistingMember() throws Exception {
            // given
            given(chatRoomCommandUseCase.join(roomId1, USER_ID))
                    .willReturn(false);

            // when & then
            mockMvc.perform(post("/chat/room/{roomId}/members", roomId1)
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isNoContent());

            verify(chatRoomCommandUseCase).join(roomId1, USER_ID);
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void joinWithoutUserHeader() throws Exception {
            mockMvc.perform(post("/chat/room/{roomId}/members", roomId1))
                    .andExpect(status().isBadRequest());

            verify(chatRoomCommandUseCase, never()).join(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("DELETE /chat/room/{roomId}/members")
    class LeaveTest {

        @Test
        @DisplayName("채팅방을 나가면 204 No Content를 반환한다")
        void leave() throws Exception {
            mockMvc.perform(delete("/chat/room/{roomId}/members", roomId1)
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isNoContent());

            verify(chatRoomCommandUseCase).leave(roomId1, USER_ID);
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void leaveWithoutUserHeader() throws Exception {
            mockMvc.perform(delete("/chat/room/{roomId}/members", roomId1))
                    .andExpect(status().isBadRequest());

            verify(chatRoomCommandUseCase, never()).leave(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("PUT /chat/room/{roomId}/activity")
    class ActivityTest {

        @Test
        @DisplayName("활동 정보를 갱신하고 204 No Content를 반환한다")
        void activity() throws Exception {
            mockMvc.perform(put("/chat/room/{roomId}/activity", roomId1)
                            .header("X-User-Id", USER_ID)
                            .param("lastMsgSeq", "10")
                            .param("lastMsgMs", "1717000000000"))
                    .andExpect(status().isNoContent());

            verify(chatRoomCommandUseCase)
                    .activity(roomId1, USER_ID, 10L, 1_717_000_000_000L);
        }

        @Test
        @DisplayName("필수 파라미터가 없으면 400을 반환한다")
        void activityWithoutRequiredParam() throws Exception {
            mockMvc.perform(put("/chat/room/{roomId}/activity", roomId1)
                            .header("X-User-Id", USER_ID)
                            .param("lastMsgSeq", "10"))
                    .andExpect(status().isBadRequest());

            verify(chatRoomCommandUseCase, never())
                    .activity(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void activityWithoutUserHeader() throws Exception {
            mockMvc.perform(put("/chat/room/{roomId}/activity", roomId1)
                            .param("lastMsgSeq", "10")
                            .param("lastMsgMs", "1717000000000"))
                    .andExpect(status().isBadRequest());

            verify(chatRoomCommandUseCase, never())
                    .activity(anyString(), anyString(), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("POST /chat/room")
    class CreateTest {

        @Test
        @DisplayName("채팅방을 생성하면 201 Created를 반환한다")
        void create() throws Exception {
            // given
            given(chatRoomQueryUseCase.existsByTitle("새 채팅방"))
                    .willReturn(false);

            String body = """
                {
                  "title": "새 채팅방",
                  "description": "새 설명",
                  "category": "%s"
                }
                """.formatted(category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/home"));

            verify(chatRoomQueryUseCase, atLeastOnce())
                    .existsByTitle("새 채팅방");

            verify(chatRoomCommandUseCase)
                    .save(eq(HOST_ID), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 title이 없으면 400과 title 검증 에러를 반환한다")
        void createWithoutTitle() throws Exception {
            // given
            String body = """
                {
                  "description": "새 설명",
                  "category": "%s"
                }
                """.formatted(category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 title이 공백이면 400과 title 검증 에러를 반환한다")
        void createBlankTitle() throws Exception {
            // given
            String body = """
                {
                  "title": "   ",
                  "description": "새 설명",
                  "category": "%s"
                }
                """.formatted(category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 description이 없으면 400과 description 검증 에러를 반환한다")
        void createWithoutDescription() throws Exception {
            // given
            String body = """
                {
                  "title": "새 채팅방",
                  "category": "%s"
                }
                """.formatted(category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("description"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 description이 공백이면 400과 description 검증 에러를 반환한다")
        void createBlankDescription() throws Exception {
            // given
            String body = """
                {
                  "title": "새 채팅방",
                  "description": "   ",
                  "category": "%s"
                }
                """.formatted(category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("description"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 category가 없으면 400과 category 검증 에러를 반환한다")
        void createWithoutCategory() throws Exception {
            // given
            String body = """
                {
                  "title": "새 채팅방",
                  "description": "새 설명"
                }
                """;

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("category"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotNull"));

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 title이 100자를 초과하면 400과 title Size 검증 에러를 반환한다")
        void createTitleTooLong() throws Exception {
            // given
            String longTitle = "가".repeat(101);

            String body = """
                {
                  "title": "%s",
                  "description": "새 설명",
                  "category": "%s"
                }
                """.formatted(longTitle, category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].code").value("Size"));

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 description이 2000자를 초과하면 400과 description Size 검증 에러를 반환한다")
        void createDescriptionTooLong() throws Exception {
            // given
            String longDescription = "가".repeat(2001);

            String body = """
                {
                  "title": "새 채팅방",
                  "description": "%s",
                  "category": "%s"
                }
                """.formatted(longDescription, category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("description"))
                    .andExpect(jsonPath("$.errors[0].code").value("Size"));

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 title이 중복이면 400과 title UniqueChatRoomTitle 검증 에러를 반환한다")
        void createDuplicatedTitle() throws Exception {
            // given
            given(chatRoomQueryUseCase.existsByTitle("중복 채팅방"))
                    .willReturn(true);

            String body = """
                {
                  "title": "중복 채팅방",
                  "description": "새 설명",
                  "category": "%s"
                }
                """.formatted(category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .header("X-User-Id", HOST_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].code").value("UniqueChatRoomTitle"));

            verify(chatRoomQueryUseCase, atLeastOnce())
                    .existsByTitle("중복 채팅방");

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }

        @Test
        @DisplayName("생성 시 X-User-Id 헤더가 없으면 400을 반환하고 save를 호출하지 않는다")
        void createWithoutUserHeader() throws Exception {
            // given
            given(chatRoomQueryUseCase.existsByTitle("새 채팅방"))
                    .willReturn(false);

            String body = """
                {
                  "title": "새 채팅방",
                  "description": "새 설명",
                  "category": "%s"
                }
                """.formatted(category.name());

            // when & then
            mockMvc.perform(post("/chat/room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verify(chatRoomCommandUseCase, never())
                    .save(anyString(), any(ChatRoomCreateRequest.class));
        }
    }

    @Nested
    @DisplayName("PATCH /chat/room/{roomId}")
    class UpdateRoomTest {

        @Test
        @DisplayName("수정할 값이 있으면 update를 호출하고 204 No Content를 반환한다")
        void update() throws Exception {
            // given
            given(chatRoomQueryUseCase.existsByTitle("수정제목"))
                    .willReturn(false);

            String body = """
            {
              "title": "수정제목",
              "description": "수정설명",
              "category": "%s"
            }
            """.formatted(category.name());

            // when
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            // then
            ArgumentCaptor<ChatRoomUpdateCommand> captor =
                    ArgumentCaptor.forClass(ChatRoomUpdateCommand.class);

            verify(chatRoomQueryUseCase, atLeastOnce())
                    .existsByTitle("수정제목");

            verify(chatRoomCommandUseCase)
                    .update(eq(roomId1), captor.capture());

            ChatRoomUpdateCommand command = captor.getValue();

            assertThat(command.title()).isEqualTo("수정제목");
            assertThat(command.description()).isEqualTo("수정설명");
            assertThat(command.category()).isEqualTo(category);
        }

        @Test
        @DisplayName("title만 수정할 수 있다")
        void updateTitleOnly() throws Exception {
            // given
            given(chatRoomQueryUseCase.existsByTitle("수정제목"))
                    .willReturn(false);

            String body = """
            {
              "title": "수정제목"
            }
            """;

            // when
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            // then
            ArgumentCaptor<ChatRoomUpdateCommand> captor =
                    ArgumentCaptor.forClass(ChatRoomUpdateCommand.class);

            verify(chatRoomQueryUseCase, atLeastOnce())
                    .existsByTitle("수정제목");

            verify(chatRoomCommandUseCase)
                    .update(eq(roomId1), captor.capture());

            ChatRoomUpdateCommand command = captor.getValue();

            assertThat(command.title()).isEqualTo("수정제목");
            assertThat(command.description()).isNull();
            assertThat(command.category()).isNull();
        }

        @Test
        @DisplayName("description만 수정할 수 있다")
        void updateDescriptionOnly() throws Exception {
            // given
            String body = """
            {
              "description": "수정설명"
            }
            """;

            // when
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            // then
            ArgumentCaptor<ChatRoomUpdateCommand> captor =
                    ArgumentCaptor.forClass(ChatRoomUpdateCommand.class);

            verify(chatRoomCommandUseCase)
                    .update(eq(roomId1), captor.capture());

            ChatRoomUpdateCommand command = captor.getValue();

            assertThat(command.title()).isNull();
            assertThat(command.description()).isEqualTo("수정설명");
            assertThat(command.category()).isNull();

            verify(chatRoomQueryUseCase, never())
                    .existsByTitle(anyString());
        }

        @Test
        @DisplayName("category만 수정할 수 있다")
        void updateCategoryOnly() throws Exception {
            // given
            String body = """
            {
              "category": "%s"
            }
            """.formatted(category.name());

            // when
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            // then
            ArgumentCaptor<ChatRoomUpdateCommand> captor =
                    ArgumentCaptor.forClass(ChatRoomUpdateCommand.class);

            verify(chatRoomCommandUseCase)
                    .update(eq(roomId1), captor.capture());

            ChatRoomUpdateCommand command = captor.getValue();

            assertThat(command.title()).isNull();
            assertThat(command.description()).isNull();
            assertThat(command.category()).isEqualTo(category);

            verify(chatRoomQueryUseCase, never())
                    .existsByTitle(anyString());
        }

        @Test
        @DisplayName("수정할 값이 없으면 400을 반환하고 update를 호출하지 않는다")
        void updateEmptyBody() throws Exception {
            // given
            String body = "{}";

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }

        @Test
        @DisplayName("수정 시 title이 공백이면 400과 title 검증 에러를 반환한다")
        void updateBlankTitle() throws Exception {
            // given
            String body = """
            {
              "title": "   "
            }
            """;

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlankIfPresent"));

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }

        @Test
        @DisplayName("수정 시 description이 공백이면 400과 description 검증 에러를 반환한다")
        void updateBlankDescription() throws Exception {
            // given
            String body = """
            {
              "description": "   "
            }
            """;

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("description"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlankIfPresent"));

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }

        @Test
        @DisplayName("수정 시 title이 100자를 초과하면 400과 title Size 검증 에러를 반환한다")
        void updateTitleTooLong() throws Exception {
            // given
            String longTitle = "가".repeat(101);

            String body = """
            {
              "title": "%s"
            }
            """.formatted(longTitle);

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].code").value("Size"));

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }

        @Test
        @DisplayName("수정 시 description이 2000자를 초과하면 400과 description Size 검증 에러를 반환한다")
        void updateDescriptionTooLong() throws Exception {
            // given
            String longDescription = "가".repeat(2001);

            String body = """
            {
              "description": "%s"
            }
            """.formatted(longDescription);

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("description"))
                    .andExpect(jsonPath("$.errors[0].code").value("Size"));

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }

        @Test
        @DisplayName("수정 시 title이 중복이면 400과 title UniqueChatRoomTitle 검증 에러를 반환한다")
        void updateDuplicatedTitle() throws Exception {
            // given
            given(chatRoomQueryUseCase.existsByTitle("중복 제목"))
                    .willReturn(true);

            String body = """
            {
              "title": "중복 제목"
            }
            """;

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].code").value("UniqueChatRoomTitle"));

            verify(chatRoomQueryUseCase, atLeastOnce())
                    .existsByTitle("중복 제목");

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }

        @Test
        @DisplayName("roomId가 blank면 400과 roomId 검증 에러를 반환한다")
        void updateBlankRoomId() throws Exception {
            // given
            given(chatRoomQueryUseCase.existsByTitle("수정제목"))
                    .willReturn(false);

            String body = """
            {
              "title": "수정제목"
            }
            """;

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", " ")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("roomId"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }

        @Test
        @DisplayName("수정 시 잘못된 category 값이면 400을 반환하고 update를 호출하지 않는다")
        void updateInvalidCategory() throws Exception {
            // given
            String body = """
            {
              "category": "WRONG_CATEGORY"
            }
            """;

            // when & then
            mockMvc.perform(patch("/chat/room/{roomId}", roomId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verify(chatRoomCommandUseCase, never())
                    .update(anyString(), any(ChatRoomUpdateCommand.class));
        }
    }

    @Nested
    @DisplayName("DELETE /chat/room/{roomId}")
    class DeleteRoomTest {

        @Test
        @DisplayName("채팅방을 삭제하고 204 No Content를 반환한다")
        void deleteRoom() throws Exception {
            mockMvc.perform(delete("/chat/room/{roomId}", roomId1))
                    .andExpect(status().isNoContent());

            verify(chatRoomCommandUseCase).delete(roomId1);
        }
    }

    private ChatRoom chatRoom(String id, String title, long msgCnt) {
        return ChatRoom.builder()
                .id(id)
                .hostId(HOST_ID)
                .title(title)
                .description("설명")
                .category(category)
                .memberIds(Set.of(HOST_ID))
                .msgCnt(msgCnt)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MyChatRoomSummary myChatRoomSummary(String id, String title, Long unreadMsgCnt) {
        return MyChatRoomSummary.builder()
                .id(id)
                .hostId(USER_ID)
                .title(title)
                .description("설명")
                .category(ChatRoomCategory.FREE)
                .memberCnt(1)
                .lastMsgContent("마지막 메시지")
                .lastMsgCreatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .unreadMsgCnt(unreadMsgCnt)
                .build();
    }
}