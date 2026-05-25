package chatroom.adapter.out;

import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.example.common.test.config.TestBootApplication;
import config.TestRedisConfig;
import org.example.chat.chatroom.adapter.out.cache.RedisChatRoomAdapter;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataRedisTest(properties = {"spring.data.redis.repositories.enabled=false"})
@ContextConfiguration(
        classes = {TestBootApplication.class, TestRedisConfig.class},
        initializers = RedisTestContainerInitializer.class
)
class RedisChatRoomAdapterTest {

    @Autowired
    private RedisChatRoomAdapter sut;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final String ROOM_ID = "room-1";
    private final String ROOM_ID_2 = "room-2";
    private final String HOST_ID = "host-1";
    private final String HOST_ID_2 = "host-2";
    private final String MEMBER_ID = "member-1";
    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    @BeforeEach
    void setUp() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @Nested
    @DisplayName("save / find")
    class SaveFindTest {

        @Test
        @DisplayName("save 후 findById로 채팅방을 조회할 수 있다")
        void saveAndFindById() {
            // given
            String title = "테스트방";
            String description = "테스트 설명";
            ChatRoom room = newRoom(ROOM_ID, title);

            // when
            sut.save(room);

            // then
            Optional<ChatRoom> found = sut.findById(ROOM_ID);

            assertThat(found).isPresent();

            ChatRoom actual = found.get();

            assertThat(actual.getId()).isEqualTo(ROOM_ID);
            assertThat(actual.getHostId()).isEqualTo(HOST_ID);
            assertThat(actual.getTitle()).isEqualTo(title);
            assertThat(actual.getDescription()).isEqualTo(description);
            assertThat(actual.getCategory()).isEqualTo(category);
            assertThat(actual.getMsgCnt()).isEqualTo(0L);
            assertThat(actual.getMemberIds()).containsExactlyInAnyOrder(HOST_ID);
        }

        @Test
        @DisplayName("save 후 title unique index에 title이 저장된다")
        void saveAndExistsByTitle() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");

            // when
            sut.save(room);

            // then
            Optional<Boolean> exists = sut.existsByTitle("테스트방");

            assertThat(exists).contains(true);
        }

        @Test
        @DisplayName("save 후 인기 채팅방 목록에서 조회된다")
        void saveAndListMostPopular() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");

            // when
            sut.save(room);

            // then
            List<ChatRoom> rooms = sut.listMostPopular(category, 10);

            assertThat(rooms)
                    .extracting(ChatRoom::getId)
                    .contains(ROOM_ID);
        }

        @Test
        @DisplayName("다른 채팅방이 같은 title로 save되면 실패한다")
        void saveDuplicateTitleDifferentRoomFails() {
            // given
            ChatRoom room1 = newRoom(ROOM_ID, HOST_ID, "중복제목");
            ChatRoom room2 = newRoom(ROOM_ID_2, HOST_ID_2, "중복제목");

            sut.save(room1);

            // when & then
            assertThatThrownBy(() -> sut.save(room2))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("chatroom save() failed");
        }

        @Test
        @DisplayName("같은 채팅방이 같은 title로 다시 save되면 멱등적으로 성공한다")
        void saveSameRoomSameTitleIsIdempotent() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");

            sut.save(room);

            // when
            sut.save(room);

            // then
            Optional<ChatRoom> found = sut.findById(ROOM_ID);

            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("테스트방");
        }
    }

    @Nested
    @DisplayName("warmUp")
    class WarmUpTest {

        @Test
        @DisplayName("warmUp은 채팅방 info, title index, popular index를 복구한다")
        void warmUp() {
            // given
            ChatRoom room = ChatRoom.builder()
                    .id(ROOM_ID)
                    .hostId(HOST_ID)
                    .title("워밍업방")
                    .description("워밍업 설명")
                    .category(category)
                    .memberIds(Set.of(HOST_ID, MEMBER_ID))
                    .msgCnt(12L)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            // when
            sut.warmUp(room);

            // then
            ChatRoom found = sut.findById(ROOM_ID).orElseThrow();

            assertThat(found.getId()).isEqualTo(ROOM_ID);
            assertThat(found.getTitle()).isEqualTo("워밍업방");
            assertThat(found.getDescription()).isEqualTo("워밍업 설명");
            assertThat(found.getMemberIds()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
            assertThat(found.getMsgCnt()).isEqualTo(12L);

            assertThat(sut.existsByTitle("워밍업방")).contains(true);
            assertThat(sut.listMostPopular(category, 10))
                    .extracting(ChatRoom::getId)
                    .contains(ROOM_ID);
        }

        @Test
        @DisplayName("warmUpList는 여러 채팅방을 한 번에 캐시에 적재한다")
        void warmUpList() {
            // given
            ChatRoom room1 = ChatRoom.builder()
                    .id("room-1")
                    .hostId("host-1")
                    .title("방1")
                    .description("설명1")
                    .category(category)
                    .memberIds(Set.of("host-1"))
                    .msgCnt(10L)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            ChatRoom room2 = ChatRoom.builder()
                    .id("room-2")
                    .hostId("host-2")
                    .title("방2")
                    .description("설명2")
                    .category(category)
                    .memberIds(Set.of("host-2"))
                    .msgCnt(20L)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            // when
            sut.warmUpList(
                    List.of(room1, room2),
                    Map.of(
                            "room-1", 10.0,
                            "room-2", 20.0
                    )
            );

            // then
            assertThat(sut.findById("room-1")).isPresent();
            assertThat(sut.findById("room-2")).isPresent();

            assertThat(sut.existsByTitle("방1")).contains(true);
            assertThat(sut.existsByTitle("방2")).contains(true);

            assertThat(sut.listMostPopular(category, 10))
                    .extracting(ChatRoom::getId)
                    .contains("room-1", "room-2");
        }

    }

    @Nested
    @DisplayName("update")
    class UpdateTest {

        @Test
        @DisplayName("title이 없는 update는 hash 정보만 갱신한다")
        void updateWithoutTitle() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);

            // when
            sut.update(
                    ROOM_ID,
                    Map.of("description", "수정된 설명"),
                    null
            );

            // then
            ChatRoom found = sut.findById(ROOM_ID).orElseThrow();

            assertThat(found.getTitle()).isEqualTo("테스트방");
            assertThat(found.getDescription()).isEqualTo("수정된 설명");
            assertThat(sut.existsByTitle("테스트방")).contains(true);
        }

        @Test
        @DisplayName("title이 있는 update는 title unique index와 hash를 함께 갱신한다")
        void updateWithTitle() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "기존제목");
            sut.save(room);

            // when
            sut.update(
                    ROOM_ID,
                    Map.of(
                            "title", "수정제목",
                            "description", "수정된 설명"
                    ),
                    "기존제목"
            );

            // then
            ChatRoom found = sut.findById(ROOM_ID).orElseThrow();

            assertThat(found.getTitle()).isEqualTo("수정제목");
            assertThat(found.getDescription()).isEqualTo("수정된 설명");

            assertThat(sut.existsByTitle("기존제목")).contains(false);
            assertThat(sut.existsByTitle("수정제목")).contains(true);
        }

        @Test
        @DisplayName("이미 사용 중인 title로 update하면 실패한다")
        void updateDuplicateTitleFails() {
            // given
            ChatRoom room1 = newRoom(ROOM_ID, HOST_ID, "기존제목");
            ChatRoom room2 = newRoom(ROOM_ID_2, HOST_ID_2, "이미있는제목");

            sut.save(room1);
            sut.save(room2);

            // when & then
            assertThatThrownBy(() ->
                    sut.update(
                            ROOM_ID,
                            Map.of("title", "이미있는제목"),
                            "기존제목"
                    )
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("chatroom update() failed");
        }

    }

    @Nested
    @DisplayName("join / leave")
    class MemberTest {

        @Test
        @DisplayName("join을 호출하면 memberIds에 멤버가 추가된다")
        void join() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);

            // when
            sut.join(ROOM_ID, MEMBER_ID);

            // then
            ChatRoom found = sut.findById(ROOM_ID).orElseThrow();

            assertThat(found.getMemberIds())
                    .containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("같은 멤버를 여러 번 join해도 중복 추가되지 않는다")
        void joinIdempotent() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);

            // when
            sut.join(ROOM_ID, MEMBER_ID);
            sut.join(ROOM_ID, MEMBER_ID);

            // then
            ChatRoom found = sut.findById(ROOM_ID).orElseThrow();

            assertThat(found.getMemberIds())
                    .containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("leave를 호출하면 memberIds에서 멤버가 제거되고 활동 캐시도 제거된다")
        void leave() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);
            sut.join(ROOM_ID, MEMBER_ID);

            sut.updateLastRead(ROOM_ID, MEMBER_ID, 10L);
            sut.updateRecentScore(ROOM_ID, MEMBER_ID, 1_717_000_000_000L);

            assertThat(sut.getLastMsgSeq(ROOM_ID, MEMBER_ID)).contains(10L);
            assertThat(sut.listLatestActive(MEMBER_ID, 10))
                    .extracting(ChatRoom::getId)
                    .contains(ROOM_ID);

            // when
            boolean result = sut.leave(ROOM_ID, MEMBER_ID);

            // then
            assertThat(result).isTrue();

            ChatRoom found = sut.findById(ROOM_ID).orElseThrow();

            assertThat(found.getMemberIds()).containsExactly(HOST_ID);
            assertThat(sut.getLastMsgSeq(ROOM_ID, MEMBER_ID)).isEmpty();
            assertThat(sut.listLatestActive(MEMBER_ID, 10))
                    .extracting(ChatRoom::getId)
                    .doesNotContain(ROOM_ID);
        }

    }

    @Nested
    @DisplayName("active / activity invalidate")
    class ActivityTest {

        @Test
        @DisplayName("updateLastRead 후 getLastMsgSeq로 마지막 읽은 seq를 조회할 수 있다")
        void updateLastRead() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);

            // when
            sut.updateLastRead(ROOM_ID, HOST_ID, 15L);

            // then
            assertThat(sut.getLastMsgSeq(ROOM_ID, HOST_ID)).contains(15L);
        }

        @Test
        @DisplayName("updateRecentScore 후 최근 활동 채팅방 목록에서 조회된다")
        void updateRecentScore() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);

            // when
            sut.updateRecentScore(ROOM_ID, HOST_ID, 1_717_000_000_000L);

            // then
            List<ChatRoom> rooms = sut.listLatestActive(HOST_ID, 10);

            assertThat(rooms)
                    .extracting(ChatRoom::getId)
                    .contains(ROOM_ID);
        }

        @Test
        @DisplayName("invalidateActivity는 lastRead와 recent index를 제거한다")
        void invalidateActivity() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);

            sut.updateLastRead(ROOM_ID, HOST_ID, 10L);
            sut.updateRecentScore(ROOM_ID, HOST_ID, 1_717_000_000_000L);

            // when
            sut.invalidateActivity(ROOM_ID, HOST_ID);

            // then
            assertThat(sut.getLastMsgSeq(ROOM_ID, HOST_ID)).isEmpty();
            assertThat(sut.listLatestActive(HOST_ID, 10))
                    .extracting(ChatRoom::getId)
                    .doesNotContain(ROOM_ID);
        }
    }

    @Nested
    @DisplayName("delete / invalidate")
    class DeleteInvalidateTest {

        @Test
        @DisplayName("delete는 채팅방 정보, title index, 인기 index, 활동 index를 제거한다")
        void delete() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "삭제방");
            sut.save(room);
            sut.join(ROOM_ID, MEMBER_ID);

            sut.updateRecentScore(ROOM_ID, HOST_ID, 1_717_000_000_000L);
            sut.updateRecentScore(ROOM_ID, MEMBER_ID, 1_717_000_000_001L);

            assertThat(sut.findById(ROOM_ID)).isPresent();
            assertThat(sut.existsByTitle("삭제방")).contains(true);

            // when
            sut.delete(
                    ROOM_ID,
                    category,
                    "삭제방",
                    Set.of(HOST_ID, MEMBER_ID)
            );

            // then
            assertThat(sut.findById(ROOM_ID)).isEmpty();
            assertThat(sut.existsByTitle("삭제방")).contains(false);

            assertThat(sut.listMostPopular(category, 10))
                    .extracting(ChatRoom::getId)
                    .doesNotContain(ROOM_ID);

            assertThat(sut.listLatestActive(HOST_ID, 10))
                    .extracting(ChatRoom::getId)
                    .doesNotContain(ROOM_ID);

            assertThat(sut.listLatestActive(MEMBER_ID, 10))
                    .extracting(ChatRoom::getId)
                    .doesNotContain(ROOM_ID);
        }

        @Test
        @DisplayName("invalidateInfo는 채팅방 info hash를 제거한다")
        void invalidateInfo() {
            // given
            ChatRoom room = newRoom(ROOM_ID, "테스트방");
            sut.save(room);

            assertThat(sut.findById(ROOM_ID)).isPresent();

            // when
            sut.invalidateInfo(ROOM_ID);

            // then
            assertThat(sut.findById(ROOM_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("recoverUpdate")
    class RecoverUpdateTest {

        @Test
        @DisplayName("recoverUpdate는 채팅방 hash, title index, popular index를 복구한다")
        void recoverUpdate() {
            // given
            ChatRoom oldRoom = newRoom(ROOM_ID, "기존제목");
            sut.save(oldRoom);

            ChatRoom recovered = ChatRoom.builder()
                    .id(ROOM_ID)
                    .hostId(HOST_ID)
                    .title("복구제목")
                    .description("복구된 설명")
                    .category(category)
                    .memberIds(Set.of(HOST_ID, MEMBER_ID))
                    .msgCnt(7L)
                    .createdAt(oldRoom.getCreatedAt())
                    .build();

            // when
            sut.recoverUpdate(recovered, "기존제목");

            // then
            ChatRoom found = sut.findById(ROOM_ID).orElseThrow();

            assertThat(found.getTitle()).isEqualTo("복구제목");
            assertThat(found.getDescription()).isEqualTo("복구된 설명");
            assertThat(found.getMemberIds()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
            assertThat(found.getMsgCnt()).isEqualTo(7L);

            assertThat(sut.existsByTitle("기존제목")).contains(false);
            assertThat(sut.existsByTitle("복구제목")).contains(true);

            assertThat(sut.listMostPopular(category, 10))
                    .extracting(ChatRoom::getId)
                    .contains(ROOM_ID);
        }
    }

    @Nested
    @DisplayName("pagination")
    class PaginationTest {

        @Test
        @DisplayName("listNextPopular는 같은 popularity에서 lastId보다 작은 id를 우선 조회한다")
        void listNextPopular() {
            // given
            ChatRoom roomA = newRoom("room-a", "A");
            ChatRoom roomB = newRoom("room-b", "B");
            ChatRoom roomC = newRoom("room-c", "C");

            sut.save(roomA);
            sut.save(roomB);
            sut.save(roomC);

            // when
            List<ChatRoom> result = sut.listNextPopular(category, "room-c", 0L, 2);

            // then
            assertThat(result)
                    .extracting(ChatRoom::getId)
                    .contains("room-b", "room-a");
        }

        @Test
        @DisplayName("listActiveBefore는 같은 score에서 lastId보다 작은 id를 우선 조회한다")
        void listActiveBefore() {
            // given
            ChatRoom roomA = newRoom("room-a", "A");
            ChatRoom roomB = newRoom("room-b", "B");
            ChatRoom roomC = newRoom("room-c", "C");

            sut.save(roomA);
            sut.save(roomB);
            sut.save(roomC);

            sut.updateRecentScore("room-a", HOST_ID, 1000L);
            sut.updateRecentScore("room-b", HOST_ID, 1000L);
            sut.updateRecentScore("room-c", HOST_ID, 1000L);

            // when
            List<ChatRoom> result = sut.listActiveBefore(HOST_ID, "room-c", 1000L, 2);

            // then
            assertThat(result)
                    .extracting(ChatRoom::getId)
                    .contains("room-b", "room-a");
        }
    }

    private ChatRoom newRoom(String id, String title) {
        return newRoom(id, HOST_ID, title);
    }

    private ChatRoom newRoom(String id, String hostId, String title) {
        return ChatRoom.ofNewRoom(
                id,
                hostId,
                title,
                "테스트 설명",
                category
        );
    }
}