package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessage;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chat.chatroom.application.service.result.MyChatRoomState;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MongoChatRoomAdapterUnitTest {

    @Mock
    private MongoChatRoomRepository chatRoomRepository;

    @Mock
    private MongoChatRoomMembershipRepository membershipRepository;

    @Mock
    private MongoChatMessageRepository chatMessageRepository;

    @InjectMocks
    private MongoChatRoomAdapter sut;

    private final ObjectId roomId1 = new ObjectId("100000000000000000000001");
    private final ObjectId roomId2 = new ObjectId("100000000000000000000002");

    private final ObjectId messageId1 = new ObjectId("200000000000000000000001");
    private final ObjectId messageId2 = new ObjectId("200000000000000000000002");

    private final String memberId = "member-1";
    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    private final LocalDateTime roomCreatedAt =
            LocalDateTime.of(2026, 1, 1, 0, 0);

    private final Instant messageCreatedAt1 =
            Instant.parse("2026-01-01T01:00:00Z");

    private final Instant messageCreatedAt2 =
            Instant.parse("2026-01-01T02:00:00Z");

    @Nested
    @DisplayName("findById")
    class FindByIdTest {

        @Test
        @DisplayName("primary repository로 방을 조회한다")
        void findById_shouldUsePrimaryRepository() {
            MongoChatRoom room = room(roomId1, 10L);

            given(chatRoomRepository.findByIdAndDeletedFalse(roomId1))
                    .willReturn(Optional.of(room));

            Optional<ChatRoom> actual = sut.findById(roomId1.toHexString());

            assertThat(actual).isPresent();
            assertThat(actual.orElseThrow().getId()).isEqualTo(roomId1.toHexString());

            then(chatRoomRepository).should()
                    .findByIdAndDeletedFalse(roomId1);
            then(chatRoomRepository).should(never())
                    .findByIdAndDeletedFalseFromSecondary(any());
        }
    }

    @Nested
    @DisplayName("findByIdWithLatestMessage")
    class FindByIdWithLatestMessageTest {

        @Test
        @DisplayName("primary room 조회 후 primary latest message를 붙인다")
        void findByIdWithLatestMessage_shouldUsePrimaryRoomAndPrimaryLatestMessage() {
            MongoChatRoom room = room(roomId1, 10L);
            MongoChatMessage latest = message(
                    messageId1,
                    roomId1,
                    "latest-message",
                    messageCreatedAt1
            );

            given(chatRoomRepository.findByIdAndDeletedFalse(roomId1))
                    .willReturn(Optional.of(room));
            given(chatMessageRepository.findTopByRoomIdAndDeletedFalseOrderByCreatedAtDescIdDesc(roomId1))
                    .willReturn(Optional.of(latest));

            Optional<ChatRoom> actual =
                    sut.findByIdWithLatestMessage(roomId1.toHexString());

            assertThat(actual).isPresent();
            assertThat(actual.orElseThrow().getId()).isEqualTo(roomId1.toHexString());

            then(chatRoomRepository).should()
                    .findByIdAndDeletedFalse(roomId1);
            then(chatMessageRepository).should()
                    .findTopByRoomIdAndDeletedFalseOrderByCreatedAtDescIdDesc(roomId1);

            then(chatRoomRepository).should(never())
                    .findByIdAndDeletedFalseFromSecondary(any());
            then(chatMessageRepository).should(never())
                    .findLatestByRoomIdFromSecondary(any());
        }
    }

    @Nested
    @DisplayName("listPopularRooms")
    class ListPopularRoomsTest {

        @Test
        @DisplayName("인기방 첫 페이지는 room primary, latest message primary를 사용한다")
        void listPopularRooms_shouldUsePrimaryRoomAndPrimaryLatestMessages() {
            MongoChatRoom room1 = room(roomId1, 30L);
            MongoChatRoom room2 = room(roomId2, 20L);

            MongoChatMessage message1 = message(
                    messageId1,
                    roomId1,
                    "room1-latest",
                    messageCreatedAt1
            );
            MongoChatMessage message2 = message(
                    messageId2,
                    roomId2,
                    "room2-latest",
                    messageCreatedAt2
            );

            given(chatRoomRepository.listPopularRooms(category, 0, 10))
                    .willReturn(List.of(room1, room2));
            given(chatMessageRepository.listLatestMessagesByRoomIds(List.of(roomId1, roomId2)))
                    .willReturn(List.of(message1, message2));

            List<ChatRoom> actual = sut.listPopularRooms(category, 10);

            assertThat(actual).hasSize(2);
            assertThat(actual)
                    .extracting(ChatRoom::getId)
                    .containsExactly(
                            roomId1.toHexString(),
                            roomId2.toHexString()
                    );

            then(chatRoomRepository).should()
                    .listPopularRooms(category, 0, 10);
            then(chatMessageRepository).should()
                    .listLatestMessagesByRoomIds(List.of(roomId1, roomId2));

            then(chatRoomRepository).should(never())
                    .listPopularRoomsAfter(any(), any(), anyLong(), anyInt());
            then(chatMessageRepository).should(never())
                    .listLatestMessagesByRoomIdsFromSecondary(any());
        }

        @Test
        @DisplayName("조회된 방이 없으면 latest message를 조회하지 않는다")
        void listPopularRooms_shouldNotFetchLatestMessagesWhenRoomsEmpty() {
            given(chatRoomRepository.listPopularRooms(category, 0, 10))
                    .willReturn(List.of());

            List<ChatRoom> actual = sut.listPopularRooms(category, 10);

            assertThat(actual).isEmpty();

            then(chatMessageRepository).should(never())
                    .listLatestMessagesByRoomIds(any());
            then(chatMessageRepository).should(never())
                    .listLatestMessagesByRoomIdsFromSecondary(any());
        }
    }

    @Nested
    @DisplayName("listPopularRoomsAfter")
    class ListPopularRoomsAfterTest {

        @Test
        @DisplayName("인기방 과거 페이지는 room secondary, latest message secondary를 사용한다")
        void listPopularRoomsAfter_shouldUseSecondaryRoomAndSecondaryLatestMessages() {
            MongoChatRoom room1 = room(roomId1, 30L);
            MongoChatRoom room2 = room(roomId2, 20L);

            MongoChatMessage message1 = message(
                    messageId1,
                    roomId1,
                    "room1-latest",
                    messageCreatedAt1
            );
            MongoChatMessage message2 = message(
                    messageId2,
                    roomId2,
                    "room2-latest",
                    messageCreatedAt2
            );

            given(chatRoomRepository.listPopularRoomsAfter(
                    category,
                    roomId1.toHexString(),
                    30L,
                    10
            )).willReturn(List.of(room1, room2));

            given(chatMessageRepository.listLatestMessagesByRoomIdsFromSecondary(
                    List.of(roomId1, roomId2)
            )).willReturn(List.of(message1, message2));

            List<ChatRoom> actual = sut.listPopularRoomsAfter(
                    category,
                    roomId1.toHexString(),
                    30L,
                    10
            );

            assertThat(actual).hasSize(2);
            assertThat(actual)
                    .extracting(ChatRoom::getId)
                    .containsExactly(
                            roomId1.toHexString(),
                            roomId2.toHexString()
                    );

            then(chatRoomRepository).should()
                    .listPopularRoomsAfter(
                            category,
                            roomId1.toHexString(),
                            30L,
                            10
                    );
            then(chatMessageRepository).should()
                    .listLatestMessagesByRoomIdsFromSecondary(List.of(roomId1, roomId2));

            then(chatMessageRepository).should(never())
                    .listLatestMessagesByRoomIds(any());
        }

        @Test
        @DisplayName("조회된 방이 없으면 secondary latest message를 조회하지 않는다")
        void listPopularRoomsAfter_shouldNotFetchLatestMessagesWhenRoomsEmpty() {
            given(chatRoomRepository.listPopularRoomsAfter(
                    category,
                    roomId1.toHexString(),
                    30L,
                    10
            )).willReturn(List.of());

            List<ChatRoom> actual = sut.listPopularRoomsAfter(
                    category,
                    roomId1.toHexString(),
                    30L,
                    10
            );

            assertThat(actual).isEmpty();

            then(chatMessageRepository).should(never())
                    .listLatestMessagesByRoomIds(any());
            then(chatMessageRepository).should(never())
                    .listLatestMessagesByRoomIdsFromSecondary(any());
        }
    }

    @Nested
    @DisplayName("listMyRoomStates")
    class ListMyRoomStatesTest {

        @Test
        @DisplayName("사용자 membership 을 읽고 방과 최신 메시지를 secondary 에서 붙여 반환한다")
        void listMyRoomStates_shouldUseSecondaryRoomAndMessage() {
            MongoChatRoomMembership membership1 = membership(roomId1, memberId, 30L);
            MongoChatRoomMembership membership2 = membership(roomId2, memberId, 0L);

            MongoChatRoom room1 = room(roomId1, 30L);
            MongoChatRoom room2 = room(roomId2, 20L);

            MongoChatMessage message1 = message(messageId1, roomId1, "room1-latest", messageCreatedAt1);
            MongoChatMessage message2 = message(messageId2, roomId2, "room2-latest", messageCreatedAt2);

            given(membershipRepository.listMemberships(memberId, 10))
                    .willReturn(List.of(membership1, membership2));
            given(chatRoomRepository.findByIdAndDeletedFalseFromSecondary(roomId1))
                    .willReturn(Optional.of(room1));
            given(chatRoomRepository.findByIdAndDeletedFalseFromSecondary(roomId2))
                    .willReturn(Optional.of(room2));
            given(chatMessageRepository.findLatestByRoomIdFromSecondary(roomId1))
                    .willReturn(Optional.of(message1));
            given(chatMessageRepository.findLatestByRoomIdFromSecondary(roomId2))
                    .willReturn(Optional.of(message2));

            List<MyChatRoomState> actual = sut.listMyRoomStates(memberId, 10);

            assertThat(actual)
                    .extracting(state -> state.room().getId(), MyChatRoomState::lastMsgReadSeq)
                    .containsExactly(
                            tuple(roomId1.toHexString(), 30L),
                            tuple(roomId2.toHexString(), 0L)
                    );

            then(membershipRepository).should().listMemberships(memberId, 10);
            then(chatRoomRepository).should(never()).findByIdAndDeletedFalse(any());
            then(chatMessageRepository).should(never())
                    .findTopByRoomIdAndDeletedFalseOrderByCreatedAtDescIdDesc(any());
        }

        @Test
        @DisplayName("읽음 위치가 비어 있으면 0으로 본다")
        void listMyRoomStates_shouldDefaultMissingReadSeqToZero() {
            given(membershipRepository.listMemberships(memberId, 10))
                    .willReturn(List.of(membership(roomId1, memberId, null)));
            given(chatRoomRepository.findByIdAndDeletedFalseFromSecondary(roomId1))
                    .willReturn(Optional.of(room(roomId1, 30L)));
            given(chatMessageRepository.findLatestByRoomIdFromSecondary(roomId1))
                    .willReturn(Optional.empty());

            List<MyChatRoomState> actual = sut.listMyRoomStates(memberId, 10);

            assertThat(actual).singleElement()
                    .extracting(MyChatRoomState::lastMsgReadSeq)
                    .isEqualTo(0L);
        }
    }

    private MongoChatRoom room(ObjectId roomId, long msgCnt) {
        return MongoChatRoom.fromDomain(
                ChatRoom.rehydrate(
                        roomId.toHexString(),
                        "host-id",
                        "title-" + roomId.toHexString(),
                        "description",
                        category,
                        Set.of("host-id", memberId),
                        msgCnt,
                        roomCreatedAt
                )
        );
    }

    private MongoChatMessage message(
            ObjectId messageId,
            ObjectId roomId,
            String content,
            Instant createdAt
    ) {
        return MongoChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .writerId("writer-1")
                .content(content)
                .createdAt(createdAt)
                .deleted(false)
                .build();
    }

    private MongoChatRoomMembership membership(
            ObjectId roomId,
            String memberId,
            Long lastMsgReadSeq
    ) {
        return MongoChatRoomMembership.builder()
                .id(MongoChatRoomMembership.generateId(roomId.toHexString(), memberId))
                .roomId(roomId)
                .memberId(memberId)
                .lastMsgReadSeq(lastMsgReadSeq)
                .build();
    }
}
