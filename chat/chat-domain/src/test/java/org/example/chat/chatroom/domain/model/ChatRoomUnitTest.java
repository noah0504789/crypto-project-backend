package org.example.chat.chatroom.domain.model;

import org.example.chat.chatroom.domain.exception.ChatRoomAccessDeniedException;
import org.example.chat.chatroom.domain.exception.ChatRoomHostMismatchException;
import org.example.chat.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.common.time.ServiceTimeConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@ExtendWith(SpringExtension.class)
class ChatRoomUnitTest {

    private final String ROOM_ID = "room-1";
    private final String HOST_ID = "host-1";
    private final String MEMBER_ID = "member-1";
    private final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 3, 20, 52);

    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("채팅방을 생성하면 host가 memberIds에 포함되고 msgCnt는 0으로 초기화된다")
        void create_shouldInitializeChatRoom() {
            // when
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // then
            assertThat(chatRoom.getId()).isEqualTo(ROOM_ID);
            assertThat(chatRoom.getHostId()).isEqualTo(HOST_ID);
            assertThat(chatRoom.getTitle()).isEqualTo("title");
            assertThat(chatRoom.getDescription()).isEqualTo("description");
            assertThat(chatRoom.getCategory()).isEqualTo(category);
            assertThat(chatRoom.getMsgCnt()).isZero();
            assertThat(chatRoom.getMemberIds()).containsExactly(HOST_ID);
            assertThat(chatRoom.getCreatedAt()).isEqualTo(CREATED_AT);
        }
    }

    @Nested
    @DisplayName("rehydrate")
    class RehydrateTest {

        @Test
        @DisplayName("id와 category만으로 채팅방을 복원하면 msgCnt는 0으로 초기화된다")
        void rehydrate_shouldCreateChatRoomWithIdAndCategory() {
            // when
            ChatRoom chatRoom = ChatRoom.rehydrate(ROOM_ID, category, CREATED_AT);

            // then
            assertThat(chatRoom.getId()).isEqualTo(ROOM_ID);
            assertThat(chatRoom.getCategory()).isEqualTo(category);
            assertThat(chatRoom.getMsgCnt()).isZero();
            assertThat(chatRoom.getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("기본 정보로 채팅방을 복원한다")
        void rehydrate_shouldRestoreChatRoom() {
            // given
            Set<String> memberIds = Set.of(HOST_ID, MEMBER_ID);
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 7, 12, 0);

            // when
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    memberIds,
                    10L,
                    createdAt
            );

            // then
            assertThat(chatRoom.getId()).isEqualTo(ROOM_ID);
            assertThat(chatRoom.getHostId()).isEqualTo(HOST_ID);
            assertThat(chatRoom.getTitle()).isEqualTo("title");
            assertThat(chatRoom.getDescription()).isEqualTo("description");
            assertThat(chatRoom.getCategory()).isEqualTo(category);
            assertThat(chatRoom.getMemberIds()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
            assertThat(chatRoom.getMsgCnt()).isEqualTo(10L);
            assertThat(chatRoom.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("memberIds가 null이면 빈 Set으로 복원한다")
        void rehydrate_shouldUseEmptySet_whenMemberIdsIsNull() {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 7, 12, 0);

            // when
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    null,
                    10L,
                    createdAt
            );

            // then
            assertThat(chatRoom.getMemberIds()).isEmpty();
        }

        @Test
        @DisplayName("전달받은 memberIds를 방어적 복사한다")
        void rehydrate_shouldDefensivelyCopyMemberIds() {
            // given
            Set<String> memberIds = new HashSet<>();
            memberIds.add(HOST_ID);

            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 7, 12, 0);

            // when
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    memberIds,
                    10L,
                    createdAt
            );

            memberIds.add(MEMBER_ID);

            // then
            assertThat(chatRoom.getMemberIds()).containsExactly(HOST_ID);
        }
    }

    @Nested
    @DisplayName("rehydrateWithLatest")
    class RehydrateWithLatestTest {

        @Test
        @DisplayName("마지막 메시지 정보까지 포함해서 채팅방을 복원한다")
        void rehydrateWithLatest_shouldRestoreChatRoomWithLatestMessage() {
            // given
            Set<String> memberIds = Set.of(HOST_ID, MEMBER_ID);
            Instant lastMsgCreatedAt = Instant.parse("2026-07-07T03:00:00Z");
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 7, 12, 0);

            // when
            ChatRoom chatRoom = ChatRoom.rehydrateWithLatest(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    memberIds,
                    10L,
                    "msg-1",
                    "hello",
                    lastMsgCreatedAt,
                    createdAt
            );

            // then
            assertThat(chatRoom.getId()).isEqualTo(ROOM_ID);
            assertThat(chatRoom.getHostId()).isEqualTo(HOST_ID);
            assertThat(chatRoom.getTitle()).isEqualTo("title");
            assertThat(chatRoom.getDescription()).isEqualTo("description");
            assertThat(chatRoom.getCategory()).isEqualTo(category);
            assertThat(chatRoom.getMemberIds()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
            assertThat(chatRoom.getMsgCnt()).isEqualTo(10L);
            assertThat(chatRoom.getLastMsgId()).isEqualTo("msg-1");
            assertThat(chatRoom.getLastMsgContent()).isEqualTo("hello");
            assertThat(chatRoom.getLastMsgCreatedAt()).isEqualTo(lastMsgCreatedAt);
            assertThat(chatRoom.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("lastMsgId와 lastMsgContent가 null이면 빈 문자열로 복원한다")
        void rehydrateWithLatest_shouldUseEmptyString_whenLatestMessageFieldsAreNull() {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 7, 12, 0);

            // when
            ChatRoom chatRoom = ChatRoom.rehydrateWithLatest(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    null,
                    null,
                    null,
                    createdAt
            );

            // then
            assertThat(chatRoom.getLastMsgId()).isEmpty();
            assertThat(chatRoom.getLastMsgContent()).isEmpty();
            assertThat(chatRoom.getLastMsgCreatedAt()).isNull();
        }

        @Test
        @DisplayName("memberIds가 null이면 빈 Set으로 복원한다")
        void rehydrateWithLatest_shouldUseEmptySet_whenMemberIdsIsNull() {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 7, 12, 0);

            // when
            ChatRoom chatRoom = ChatRoom.rehydrateWithLatest(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    null,
                    10L,
                    "msg-1",
                    "hello",
                    Instant.parse("2026-07-07T03:00:00Z"),
                    createdAt
            );

            // then
            assertThat(chatRoom.getMemberIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPopularity")
    class GetPopularityTest {

        @Test
        @DisplayName("msgCnt가 있으면 double 값으로 popularity를 반환한다")
        void getPopularity_shouldReturnMsgCntAsDouble() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    15L,
                    LocalDateTime.now()
            );

            // when
            Double popularity = chatRoom.popularity();

            // then
            assertThat(popularity).isEqualTo(15.0);
        }

        @Test
        @DisplayName("msgCnt가 null이면 popularity는 0이다")
        void getPopularity_shouldReturnZero_whenMsgCntIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    null,
                    LocalDateTime.now()
            );

            // when
            Double popularity = chatRoom.popularity();

            // then
            assertThat(popularity).isZero();
        }
    }

    @Nested
    @DisplayName("lastMsgCreatedAtMs")
    class lastMsgCreatedAtMsTest {

        @Test
        @DisplayName("lastMsgCreatedAt이 있으면 epoch milli를 반환한다")
        void lastMsgCreatedAtMs_shouldReturnEpochMilli() {
            // given
            Instant lastMsgCreatedAt = Instant.parse("2026-07-07T03:00:00Z");

            ChatRoom chatRoom = ChatRoom.rehydrateWithLatest(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    "msg-1",
                    "hello",
                    lastMsgCreatedAt,
                    LocalDateTime.now()
            );

            // when
            long result = chatRoom.lastMsgCreatedAtMs();

            // then
            assertThat(result).isEqualTo(lastMsgCreatedAt.toEpochMilli());
        }

        @Test
        @DisplayName("lastMsgCreatedAt이 null이면 0을 반환한다")
        void lastMsgCreatedAtMs_shouldReturnZero_whenLastMsgCreatedAtIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when
            long result = chatRoom.lastMsgCreatedAtMs();

            // then
            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("createdAtInstant")
    class createdAtInstantTest {

        @Test
        @DisplayName("createdAt을 서비스 Zone 기준 Instant로 변환한다")
        void createdAtInstant_shouldConvertCreatedAtToInstant() {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 7, 12, 0);

            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    createdAt
            );

            // when
            Instant result = chatRoom.createdAtInstant();

            // then
            assertThat(result).isEqualTo(
                    ServiceTimeConverter.toInstant(createdAt)
            );
        }
    }

    @Nested
    @DisplayName("validateWritable")
    class ValidateWritableTest {

        @Test
        @DisplayName("작성자가 채팅방 멤버이면 예외가 발생하지 않는다")
        void validateWritable_shouldNotThrow_whenWriterIsMember() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID, MEMBER_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatCode(() -> chatRoom.validateWritable(MEMBER_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("작성자 id가 null이면 ChatRoomMembershipNotFoundException이 발생한다")
        void validateWritable_shouldThrow_whenWriterIdIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatThrownBy(() -> chatRoom.validateWritable(null))
                    .isInstanceOf(ChatRoomMembershipNotFoundException.class);
        }

        @Test
        @DisplayName("작성자 id가 공백이면 ChatRoomMembershipNotFoundException이 발생한다")
        void validateWritable_shouldThrow_whenWriterIdIsBlank() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatThrownBy(() -> chatRoom.validateWritable(" "))
                    .isInstanceOf(ChatRoomMembershipNotFoundException.class);
        }

        @Test
        @DisplayName("작성자가 채팅방 멤버가 아니면 ChatRoomMembershipNotFoundException이 발생한다")
        void validateWritable_shouldThrow_whenWriterIsNotMember() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatThrownBy(() -> chatRoom.validateWritable(MEMBER_ID))
                    .isInstanceOf(ChatRoomMembershipNotFoundException.class);
        }

        @Test
        @DisplayName("memberIds가 null이면 ChatRoomMembershipNotFoundException이 발생한다")
        void validateWritable_shouldThrow_whenMemberIdsIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.builder()
                    .id(ROOM_ID)
                    .memberIds(null)
                    .build();

            // when & then
            assertThatThrownBy(() -> chatRoom.validateWritable(MEMBER_ID))
                    .isInstanceOf(ChatRoomMembershipNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("validateHost")
    class ValidateHostTest {

        @Test
        @DisplayName("행위자가 host이면 예외가 발생하지 않는다")
        void validateHost_shouldNotThrow_whenActorIsHost() {
            // given
            ChatRoom chatRoom = hostRoom();

            // when & then
            assertThatCode(() -> chatRoom.validateHost(HOST_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("행위자가 host가 아니면 멤버여도 ChatRoomHostMismatchException이 발생한다")
        void validateHost_shouldThrow_whenActorIsMemberButNotHost() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID, MEMBER_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatThrownBy(() -> chatRoom.validateHost(MEMBER_ID))
                    .isInstanceOf(ChatRoomHostMismatchException.class);
        }

        @Test
        @DisplayName("행위자 id가 null이면 ChatRoomHostMismatchException이 발생한다")
        void validateHost_shouldThrow_whenActorIdIsNull() {
            // given
            ChatRoom chatRoom = hostRoom();

            // when & then
            assertThatThrownBy(() -> chatRoom.validateHost(null))
                    .isInstanceOf(ChatRoomHostMismatchException.class);
        }

        @Test
        @DisplayName("행위자 id가 공백이면 ChatRoomHostMismatchException이 발생한다")
        void validateHost_shouldThrow_whenActorIdIsBlank() {
            // given
            ChatRoom chatRoom = hostRoom();

            // when & then
            assertThatThrownBy(() -> chatRoom.validateHost(" "))
                    .isInstanceOf(ChatRoomHostMismatchException.class);
        }

        private ChatRoom hostRoom() {
            return ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );
        }
    }

    @Nested
    @DisplayName("validateMember")
    class ValidateMemberTest {

        @Test
        @DisplayName("행위자가 멤버이면 예외가 발생하지 않는다")
        void validateMember_shouldNotThrow_whenActorIsMember() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID, MEMBER_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatCode(() -> chatRoom.validateMember(MEMBER_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("행위자가 멤버가 아니면 ChatRoomAccessDeniedException이 발생한다")
        void validateMember_shouldThrow_whenActorIsNotMember() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatThrownBy(() -> chatRoom.validateMember(MEMBER_ID))
                    .isInstanceOf(ChatRoomAccessDeniedException.class);
        }

        @Test
        @DisplayName("행위자 id가 null이면 ChatRoomAccessDeniedException이 발생한다")
        void validateMember_shouldThrow_whenActorIdIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when & then
            assertThatThrownBy(() -> chatRoom.validateMember(null))
                    .isInstanceOf(ChatRoomAccessDeniedException.class);
        }

        @Test
        @DisplayName("memberIds가 null이면 ChatRoomAccessDeniedException이 발생한다")
        void validateMember_shouldThrow_whenMemberIdsIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.builder()
                    .id(ROOM_ID)
                    .memberIds(null)
                    .build();

            // when & then
            assertThatThrownBy(() -> chatRoom.validateMember(MEMBER_ID))
                    .isInstanceOf(ChatRoomAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("addMember")
    class AddMemberTest {

        @Test
        @DisplayName("새로운 멤버를 추가하면 true를 반환하고 memberIds에 추가된다")
        void addMember_shouldAddMemberAndReturnTrue() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when
            boolean result = chatRoom.addMember(MEMBER_ID);

            // then
            assertThat(result).isTrue();
            assertThat(chatRoom.getMemberIds())
                    .containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("이미 존재하는 멤버를 추가하면 false를 반환한다")
        void addMember_shouldReturnFalse_whenMemberAlreadyExists() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when
            boolean result = chatRoom.addMember(HOST_ID);

            // then
            assertThat(result).isFalse();
            assertThat(chatRoom.getMemberIds()).containsExactly(HOST_ID);
        }
    }

    @Nested
    @DisplayName("removeMember")
    class RemoveMemberTest {

        @Test
        @DisplayName("존재하는 멤버를 제거하면 true를 반환하고 memberIds에서 제거된다")
        void removeMember_shouldRemoveMemberAndReturnTrue() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID, MEMBER_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.removeMember(MEMBER_ID);

            // then
            assertThat(result).isTrue();
            assertThat(chatRoom.getMemberIds()).containsExactly(HOST_ID);
        }

        @Test
        @DisplayName("존재하지 않는 멤버를 제거하면 false를 반환한다")
        void removeMember_shouldReturnFalse_whenMemberDoesNotExist() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when
            boolean result = chatRoom.removeMember(MEMBER_ID);

            // then
            assertThat(result).isFalse();
            assertThat(chatRoom.getMemberIds()).containsExactly(HOST_ID);
        }
    }

    @Nested
    @DisplayName("hasNoMembers")
    class HasNoMembersTest {

        @Test
        @DisplayName("memberIds가 비어 있으면 true를 반환한다")
        void hasNoMembers_shouldReturnTrue_whenMemberIdsIsEmpty() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(),
                    10L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.hasNoMembers();

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("memberIds가 null이면 true를 반환한다")
        void hasNoMembers_shouldReturnTrue_whenMemberIdsIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.builder()
                    .id(ROOM_ID)
                    .memberIds(null)
                    .build();

            // when
            boolean result = chatRoom.hasNoMembers();

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("memberIds가 비어 있지 않으면 false를 반환한다")
        void hasNoMembers_shouldReturnFalse_whenMemberIdsIsNotEmpty() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when
            boolean result = chatRoom.hasNoMembers();

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isLastMember")
    class IsLastMemberTest {

        @Test
        @DisplayName("memberId가 유일한 멤버이면 true를 반환한다")
        void isLastMember_shouldReturnTrue_whenMemberIsOnlyMember() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when
            boolean result = chatRoom.isLastMember(HOST_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("memberId가 유일한 멤버가 아니면 false를 반환한다")
        void isLastMember_shouldReturnFalse_whenMemberIsNotOnlyMember() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID, MEMBER_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.isLastMember(HOST_ID);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("memberId가 멤버가 아니면 false를 반환한다")
        void isLastMember_shouldReturnFalse_whenMemberDoesNotExist() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when
            boolean result = chatRoom.isLastMember(MEMBER_ID);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("hasUnread")
    class HasUnreadTest {

        @Test
        @DisplayName("lastReadSeq가 msgCnt보다 작으면 true를 반환한다")
        void hasUnread_shouldReturnTrue_whenLastReadSeqIsLessThanMsgCnt() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.hasUnread(9L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("lastReadSeq가 msgCnt와 같으면 false를 반환한다")
        void hasUnread_shouldReturnFalse_whenLastReadSeqEqualsMsgCnt() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.hasUnread(10L);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("lastReadSeq가 msgCnt보다 크면 false를 반환한다")
        void hasUnread_shouldReturnFalse_whenLastReadSeqIsGreaterThanMsgCnt() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.hasUnread(11L);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("보관 메시지 수와 무관하게 latestMessageSeq를 기준으로 읽지 않음을 판단한다")
        void hasUnread_shouldUseLatestMessageSeq() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    5L,
                    20L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.hasUnread(10L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("lastReadSeq가 null이면 0으로 보고 읽지 않은 메시지 여부를 판단한다")
        void hasUnread_shouldTreatNullLastReadSeqAsZero() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    1L,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.hasUnread(null);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("msgCnt가 null이면 false를 반환한다")
        void hasUnread_shouldReturnFalse_whenMsgCntIsNull() {
            // given
            ChatRoom chatRoom = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    Set.of(HOST_ID),
                    null,
                    LocalDateTime.now()
            );

            // when
            boolean result = chatRoom.hasUnread(0L);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("equals & hashCode")
    class EqualsAndHashCodeTest {

        @Test
        @DisplayName("id가 같으면 같은 채팅방으로 판단한다")
        void equals_shouldReturnTrue_whenIdIsSame() {
            // given
            ChatRoom chatRoom1 = ChatRoom.rehydrate(
                    ROOM_ID,
                    HOST_ID,
                    "title-1",
                    "description-1",
                    category,
                    Set.of(HOST_ID),
                    10L,
                    LocalDateTime.now()
            );

            ChatRoom chatRoom2 = ChatRoom.rehydrate(
                    ROOM_ID,
                    "other-host",
                    "title-2",
                    "description-2",
                    category,
                    Set.of("other-member"),
                    20L,
                    LocalDateTime.now()
            );

            // when & then
            assertThat(chatRoom1).isEqualTo(chatRoom2);
            assertThat(chatRoom1).hasSameHashCodeAs(chatRoom2);
        }

        @Test
        @DisplayName("id가 다르면 다른 채팅방으로 판단한다")
        void equals_shouldReturnFalse_whenIdIsDifferent() {
            // given
            ChatRoom chatRoom1 = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            ChatRoom chatRoom2 = ChatRoom.create(
                    "room-2",
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when & then
            assertThat(chatRoom1).isNotEqualTo(chatRoom2);
        }

        @Test
        @DisplayName("id가 null이면 같은 객체가 아닌 이상 같지 않다")
        void equals_shouldReturnFalse_whenIdIsNull() {
            // given
            ChatRoom chatRoom1 = ChatRoom.builder()
                    .id(null)
                    .build();

            ChatRoom chatRoom2 = ChatRoom.builder()
                    .id(null)
                    .build();

            // when & then
            assertThat(chatRoom1).isNotEqualTo(chatRoom2);
        }

        @Test
        @DisplayName("같은 인스턴스이면 true를 반환한다")
        void equals_shouldReturnTrue_whenSameInstance() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when & then
            assertThat(chatRoom).isEqualTo(chatRoom);
        }

        @Test
        @DisplayName("null과 비교하면 false를 반환한다")
        void equals_shouldReturnFalse_whenComparedWithNull() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when & then
            assertThat(chatRoom).isNotEqualTo(null);
        }

        @Test
        @DisplayName("다른 타입과 비교하면 false를 반환한다")
        void equals_shouldReturnFalse_whenComparedWithDifferentType() {
            // given
            ChatRoom chatRoom = ChatRoom.create(
                    ROOM_ID,
                    HOST_ID,
                    "title",
                    "description",
                    category,
                    CREATED_AT
            );

            // when & then
            assertThat(chatRoom).isNotEqualTo("room-1");
        }
    }
}
