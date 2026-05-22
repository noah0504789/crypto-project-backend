package chatroom.domain;

import org.example.chatroom.domain.event.dlq.ChatRoomDlqEventList;
import org.example.chatroom.domain.event.payload.ChatRoomPayload;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.chatroom.domain.event.ChatRoomEventList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@ExtendWith(SpringExtension.class)
class ChatRoomTest {

    private final String ROOM_ID = "room-1";
    private final String HOST_ID = "host-1";
    private final String MEMBER_ID = "member-1";

    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    @Nested
    @DisplayName("생성")
    class CreateTest {

        @Test
        @DisplayName("id만으로 ChatRoom을 생성하면 기본값이 설정된다")
        void ofId() {
            // when
            ChatRoom room = ChatRoom.ofId(ROOM_ID);

            // then
            assertThat(room.getId()).isEqualTo(ROOM_ID);
            assertThat(room.getMsgCnt()).isZero();
            assertThat(room.getCreatedAt()).isNotNull();
            assertThat(room.getEventList()).isNotNull();
            assertThat(room.getDlqEventList()).isNotNull();
        }

        @Test
        @DisplayName("id와 category로 ChatRoom을 생성하면 category가 설정된다")
        void ofIdAndCategory() {
            // when
            ChatRoom room = ChatRoom.ofIdAndCategory(ROOM_ID, category);

            // then
            assertThat(room.getId()).isEqualTo(ROOM_ID);
            assertThat(room.getCategory()).isEqualTo(category);
            assertThat(room.getMsgCnt()).isZero();
            assertThat(room.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("채팅방 생성 팩토리는 host를 최초 member로 포함한다")
        void ofFullArgs() {
            // when
            ChatRoom room = ChatRoom.ofNewRoom(
                    ROOM_ID,
                    HOST_ID,
                    "테스트방",
                    "설명",
                    category
            );

            // then
            assertThat(room.getId()).isEqualTo(ROOM_ID);
            assertThat(room.getHostId()).isEqualTo(HOST_ID);
            assertThat(room.getTitle()).isEqualTo("테스트방");
            assertThat(room.getDescription()).isEqualTo("설명");
            assertThat(room.getCategory()).isEqualTo(category);
            assertThat(room.getMsgCnt()).isZero();
            assertThat(room.getMemberIds()).containsExactly(HOST_ID);
            assertThat(room.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("payload에서 ChatRoom을 복원한다")
        void fromPayload() {
            // given
            Instant createdAt = Instant.now();

            ChatRoomPayload payload = ChatRoomPayload.builder()
                    .id(ROOM_ID)
                    .hostId(HOST_ID)
                    .title("payload-title")
                    .description("payload-description")
                    .category(category)
                    .memberIds(Set.of(HOST_ID, MEMBER_ID))
                    .createdAt(createdAt)
                    .build();

            // when
            ChatRoom room = ChatRoom.fromPayload(payload);

            // then
            assertThat(room.getId()).isEqualTo(ROOM_ID);
            assertThat(room.getHostId()).isEqualTo(HOST_ID);
            assertThat(room.getTitle()).isEqualTo("payload-title");
            assertThat(room.getDescription()).isEqualTo("payload-description");
            assertThat(room.getCategory()).isEqualTo(category);
            assertThat(room.getMsgCnt()).isZero();
            assertThat(room.getMemberIds()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
            assertThat(room.getCreatedAt()).isEqualTo(payload.toLocalDateTime());
            assertThat(room.getEventList()).isNotNull();
            assertThat(room.getDlqEventList()).isNotNull();
        }

        @Test
        @DisplayName("payload의 memberIds가 null이면 빈 Set으로 복원한다")
        void fromPayloadWithNullMemberIds() {
            // given
            ChatRoomPayload payload = ChatRoomPayload.builder()
                    .id(ROOM_ID)
                    .hostId(HOST_ID)
                    .title("title")
                    .description("description")
                    .category(category)
                    .memberIds(null)
                    .createdAt(Instant.now())
                    .build();

            // when
            ChatRoom room = ChatRoom.fromPayload(payload);

            // then
            assertThat(room.getMemberIds()).isNotNull();
            assertThat(room.getMemberIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("멤버")
    class MemberTest {

        @Test
        @DisplayName("새 멤버를 추가하면 true를 반환하고 memberIds에 추가된다")
        void addMemberSuccess() {
            // given
            ChatRoom room = ChatRoom.ofNewRoom(
                    ROOM_ID,
                    HOST_ID,
                    "테스트방",
                    "설명",
                    category
            );

            // when
            boolean result = room.addMember(MEMBER_ID);

            // then
            assertThat(result).isTrue();
            assertThat(room.getMemberIds()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("이미 존재하는 멤버를 추가하면 false를 반환한다")
        void addMemberAlreadyExists() {
            // given
            ChatRoom room = ChatRoom.ofNewRoom(
                    ROOM_ID,
                    HOST_ID,
                    "테스트방",
                    "설명",
                    category
            );

            // when
            boolean result = room.addMember(HOST_ID);

            // then
            assertThat(result).isFalse();
            assertThat(room.getMemberIds()).containsExactly(HOST_ID);
        }

        @Test
        @DisplayName("존재하는 멤버를 제거하면 true를 반환하고 memberIds에서 제거된다")
        void removeMemberSuccess() {
            // given
            ChatRoom room = ChatRoom.ofNewRoom(
                    ROOM_ID,
                    HOST_ID,
                    "테스트방",
                    "설명",
                    category
            );

            room.addMember(MEMBER_ID);

            // when
            boolean result = room.removeMember(MEMBER_ID);

            // then
            assertThat(result).isTrue();
            assertThat(room.getMemberIds()).containsExactly(HOST_ID);
        }

        @Test
        @DisplayName("존재하지 않는 멤버를 제거하면 false를 반환한다")
        void removeMemberNotExists() {
            // given
            ChatRoom room = ChatRoom.ofNewRoom(
                    ROOM_ID,
                    HOST_ID,
                    "테스트방",
                    "설명",
                    category
            );

            // when
            boolean result = room.removeMember(MEMBER_ID);

            // then
            assertThat(result).isFalse();
            assertThat(room.getMemberIds()).containsExactly(HOST_ID);
        }

        @Test
        @DisplayName("멤버가 1명이고 해당 멤버이면 마지막 멤버로 판단한다")
        void isLastMemberTrue() {
            // given
            ChatRoom room = ChatRoom.ofNewRoom(
                    ROOM_ID,
                    HOST_ID,
                    "테스트방",
                    "설명",
                    category
            );

            // when
            boolean result = room.isLastMember(HOST_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("멤버가 여러 명이면 마지막 멤버가 아니다")
        void isLastMemberFalse() {
            // given
            ChatRoom room = ChatRoom.ofNewRoom(
                    ROOM_ID,
                    HOST_ID,
                    "테스트방",
                    "설명",
                    category
            );

            room.addMember(MEMBER_ID);

            // when
            boolean result = room.isLastMember(HOST_ID);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("memberIds가 비어 있으면 hasNoMembers는 true다")
        void hasNoMembersTrue() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .memberIds(new HashSet<>())
                    .eventList(new ChatRoomEventList())
                    .dlqEventList(new ChatRoomDlqEventList())
                    .build();

            // when
            boolean result = room.hasNoMembers();

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("memberIds가 null이면 hasNoMembers는 true다")
        void hasNoMembersWhenNull() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .memberIds(null)
                    .eventList(new ChatRoomEventList())
                    .dlqEventList(new ChatRoomDlqEventList())
                    .build();

            // when
            boolean result = room.hasNoMembers();

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("읽음/인기도/최신 메시지")
    class StateTest {

        @Test
        @DisplayName("msgCnt가 null이면 popularity는 0이다")
        void getPopularityWhenMsgCntNull() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .msgCnt(null)
                    .build();

            // when
            Double popularity = room.getPopularity();

            // then
            assertThat(popularity).isZero();
        }

        @Test
        @DisplayName("msgCnt가 있으면 popularity는 msgCnt의 double 값이다")
        void getPopularity() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .msgCnt(12L)
                    .build();

            // when
            Double popularity = room.getPopularity();

            // then
            assertThat(popularity).isEqualTo(12.0);
        }

        @Test
        @DisplayName("lastReadSeq가 msgCnt보다 작으면 읽지 않은 메시지가 있다")
        void hasUnreadTrue() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .msgCnt(10L)
                    .build();

            // when
            boolean result = room.hasUnread(9L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("lastReadSeq가 msgCnt와 같으면 읽지 않은 메시지가 없다")
        void hasUnreadFalse() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .msgCnt(10L)
                    .build();

            // when
            boolean result = room.hasUnread(10L);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("lastReadSeq가 null이면 0으로 판단한다")
        void hasUnreadWhenLastReadSeqNull() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .msgCnt(1L)
                    .build();

            // when
            boolean result = room.hasUnread(null);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("msgCnt가 null이면 읽지 않은 메시지가 없다고 판단한다")
        void hasUnreadWhenMsgCntNull() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .msgCnt(null)
                    .build();

            // when
            boolean result = room.hasUnread(0L);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("lastMsgCreatedAt이 null이면 0ms를 반환한다")
        void getLastMsgCreatedAtMsWhenNull() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .lastMsgCreatedAt(null)
                    .build();

            // when
            long result = room.getLastMsgCreatedAtMs();

            // then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("lastMsgCreatedAt이 있으면 epoch milli를 반환한다")
        void getLastMsgCreatedAtMs() {
            // given
            Instant instant = Instant.now();

            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .lastMsgCreatedAt(instant)
                    .build();

            // when
            long result = room.getLastMsgCreatedAtMs();

            // then
            assertThat(result).isEqualTo(instant.toEpochMilli());
        }
    }

    @Nested
    @DisplayName("시간 변환")
    class TimeTest {

        @Test
        @DisplayName("createdAt을 Instant로 변환한다")
        void toInstant() {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 5, 11, 10, 30);

            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .createdAt(createdAt)
                    .build();

            // when
            Instant result = room.toInstant();

            // then
            assertThat(result).isEqualTo(createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
    }

    @Nested
    @DisplayName("동등성")
    class EqualityTest {

        @Test
        @DisplayName("id가 같으면 같은 ChatRoom으로 판단한다")
        void equalsById() {
            // given
            ChatRoom room1 = ChatRoom.builder()
                    .id(ROOM_ID)
                    .title("A")
                    .build();

            ChatRoom room2 = ChatRoom.builder()
                    .id(ROOM_ID)
                    .title("B")
                    .build();

            // then
            assertThat(room1).isEqualTo(room2);
            assertThat(room1.hashCode()).isEqualTo(room2.hashCode());
        }

        @Test
        @DisplayName("id가 다르면 다른 ChatRoom으로 판단한다")
        void notEqualsByDifferentId() {
            // given
            ChatRoom room1 = ChatRoom.builder()
                    .id("room-1")
                    .build();

            ChatRoom room2 = ChatRoom.builder()
                    .id("room-2")
                    .build();

            // then
            assertThat(room1).isNotEqualTo(room2);
        }
    }
}