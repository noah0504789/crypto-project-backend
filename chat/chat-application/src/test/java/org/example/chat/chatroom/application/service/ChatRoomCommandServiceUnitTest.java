package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.exception.ChatRoomEventPublishException;
import org.example.chat.chatroom.application.service.command.ChatRoomActivityCommand;
import org.example.chat.chatroom.application.service.command.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomIdGeneratorPort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chat.chatroom.application.service.command.ChatRoomCreateCommand;
import org.example.chat.chatroom.application.event.ChatRoomEventList;
import org.example.chat.chatroom.application.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
import org.example.common.time.Clock;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomCommandServiceUnitTest {

    @Mock
    private ChatRoomCachePort cache;

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomQueryUseCase chatRoomQueryUseCase;

    @Mock
    private ChatRoomIdGeneratorPort idGenerator;

    @Mock
    private OutboxEventListPublishPort outboxEventListPublishPort;

    @Mock
    private Clock clock;

    @Spy
    @InjectMocks
    private ChatRoomCommandService sut;

    private final ChatRoomCategory category = ChatRoomCategory.FREE;
    private final String id = "id";
    private final String hostId = "host1";
    private final String memberId = "member1";
    private final Long lastMsgSeq = 10L;
    private final Long lastMsgMs = 100L;
    private final String oldTitle = "old-title";
    private final String newTitle = "new-title";
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 8, 3, 20, 52);

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("채팅방 id를 생성하고 ChatRoom을 만든 뒤 save에 위임한다")
        void create_shouldGenerateIdCreateDomainAndDelegateSave() {
            // given
            ChatRoomCreateCommand command = mock(ChatRoomCreateCommand.class);

            given(idGenerator.generate()).willReturn(id);
            given(command.hostId()).willReturn(hostId);
            given(command.title()).willReturn("title");
            given(command.description()).willReturn("description");
            given(command.category()).willReturn(category);
            given(clock.nowLocalDateTime()).willReturn(createdAt);

            doNothing()
                    .when(sut)
                    .save(any(ChatRoom.class));

            // when
            sut.create(command);

            // then
            then(idGenerator)
                    .should()
                    .generate();

            then(sut)
                    .should()
                    .save(argThat(domain ->
                            domain.getId().equals(id)
                                    && domain.getHostId().equals(hostId)
                                    && domain.getTitle().equals("title")
                                    && domain.getDescription().equals("description")
                                    && domain.getCategory() == category
                                    && domain.getMsgCnt().equals(0L)
                                    && domain.getMemberIds().contains(hostId)
                                    && domain.getCreatedAt().equals(createdAt)
                    ));

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("채팅방 저장 이벤트를 발행하고 캐시에 저장한 뒤 activity를 호출한다")
        void save_shouldPublishPersistedEventSaveCacheAndCallActivity() {
            // given
            ChatRoom domain = chatRoom();

            doNothing()
                    .when(sut)
                    .activity(any(ChatRoomActivityCommand.class));

            // when
            sut.save(domain);

            // then
            InOrder inOrder = inOrder(
                    outboxEventListPublishPort,
                    cache,
                    sut
            );

            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .save(domain);
            inOrder.verify(sut)
                    .activity(argThat(command ->
                            command.roomId().equals(id)
                                    && command.memberId().equals(hostId)
                                    && command.lastMsgReadSeq().equals(0L)
                                    && command.lastMsgCreatedAtMs().equals(0L)
                    ));
        }

        @Test
        @DisplayName("cache save 실패 시 fallback 이벤트를 발행하고 activity는 계속 수행한다")
        void save_shouldPublishCacheSaveFallbackEvent_whenCacheSaveFails() {
            // given
            ChatRoom domain = chatRoom();

            RuntimeException cacheException = new RuntimeException("cache save failed");

            doThrow(cacheException)
                    .when(cache)
                    .save(domain);

            doNothing()
                    .when(sut)
                    .activity(any(ChatRoomActivityCommand.class));

            // when
            sut.save(domain);

            // then
            InOrder inOrder = inOrder(
                    outboxEventListPublishPort,
                    cache,
                    sut
            );

            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .save(domain);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(sut)
                    .activity(any(ChatRoomActivityCommand.class));
        }

        @Test
        @DisplayName("채팅방 저장 이벤트 발행 중 TemporaryOutboxPersistenceException이 발생하면 그대로 전파한다")
        void save_shouldRethrowTemporaryOutboxException() {
            // given
            ChatRoom domain = chatRoom();

            TemporaryOutboxPersistenceException exception =
                    new TemporaryOutboxPersistenceException(
                            "temporary outbox failure",
                            new RuntimeException("temporary")
                    );

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.save(domain))
                    .isSameAs(exception);

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("채팅방 저장 이벤트 발행 중 일반 예외가 발생하면 ChatRoomEventPublishException으로 감싼다")
        void save_shouldWrapUnexpectedOutboxException() {
            // given
            ChatRoom domain = chatRoom();

            RuntimeException exception = new RuntimeException("outbox failed");

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.save(domain))
                    .isInstanceOf(ChatRoomEventPublishException.class)
                    .hasCause(exception);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTest {

        @Test
        @DisplayName("채팅방 수정 이벤트를 발행하고 기존 채팅방 조회 후 캐시를 수정한다")
        void update_shouldPublishUpdatedEventFindDomainAndUpdateCache() {
            // given
            ChatRoomUpdateCommand command = mock(ChatRoomUpdateCommand.class);
            ChatRoomUpdatedPayload payload = mock(ChatRoomUpdatedPayload.class);
            ChatRoom domain = chatRoomWithTitle(oldTitle);

            Map<String, Object> updateMap = Map.of("title", newTitle);

            given(command.roomId()).willReturn(id);
            given(command.myUserId()).willReturn(hostId);
            given(command.toPayload()).willReturn(payload);
            given(payload.toUpdateMap()).willReturn(updateMap);
            given(persistence.findById(id)).willReturn(Optional.of(domain));

            // when
            sut.update(command);

            // then
            InOrder inOrder = inOrder(
                    outboxEventListPublishPort,
                    persistence,
                    cache
            );

            inOrder.verify(persistence)
                    .findById(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .updateRoom(id, updateMap, oldTitle);
        }

        @Test
        @DisplayName("채팅방이 없으면 이벤트 발행 전에 ChatRoomNotFoundException이 발생하고 캐시는 수정하지 않는다")
        void update_shouldThrow_whenChatRoomNotFound() {
            // given
            ChatRoomUpdateCommand command = mock(ChatRoomUpdateCommand.class);

            given(command.roomId()).willReturn(id);
            given(persistence.findById(id)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.update(command))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            then(persistence)
                    .should()
                    .findById(id);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("cache update 실패 시 cache update fallback 이벤트를 발행한다")
        void update_shouldPublishCacheUpdateFallbackEvent_whenCacheUpdateFails() {
            // given
            ChatRoomUpdateCommand command = mock(ChatRoomUpdateCommand.class);
            ChatRoomUpdatedPayload payload = mock(ChatRoomUpdatedPayload.class);
            ChatRoom domain = chatRoomWithTitle(oldTitle);

            Map<String, Object> updateMap = Map.of("title", newTitle);

            given(command.roomId()).willReturn(id);
            given(command.myUserId()).willReturn(hostId);
            given(command.toPayload()).willReturn(payload);
            given(payload.toUpdateMap()).willReturn(updateMap);
            given(persistence.findById(id)).willReturn(Optional.of(domain));

            doThrow(new RuntimeException("cache update failed"))
                    .when(cache)
                    .updateRoom(id, updateMap, oldTitle);

            // when
            sut.update(command);

            // then
            InOrder inOrder = inOrder(
                    outboxEventListPublishPort,
                    persistence,
                    cache
            );

            inOrder.verify(persistence)
                    .findById(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .updateRoom(id, updateMap, oldTitle);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
        }
    }

    @Nested
    @DisplayName("join")
    class JoinTest {

        @Test
        @DisplayName("채팅방에 새 멤버가 가입하면 이벤트를 발행하고 캐시에 멤버십을 추가한 뒤 true를 반환한다")
        void join_shouldAddMemberPublishEventUpdateCacheAndReturnTrue() {
            // given
            ChatRoom domain = chatRoom();

            given(persistence.findById(id)).willReturn(Optional.of(domain));

            // when
            boolean result = sut.join(id, memberId);

            // then
            assertThat(result).isTrue();
            assertThat(domain.getMemberIds()).contains(memberId);

            InOrder inOrder = inOrder(
                    persistence,
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(persistence)
                    .findById(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .joinMembership(id, memberId);
        }

        @Test
        @DisplayName("이미 가입된 멤버면 false를 반환하고 이벤트와 캐시 작업을 수행하지 않는다")
        void join_shouldReturnFalse_whenAlreadyJoined() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    id,
                    hostId,
                    oldTitle,
                    "description",
                    category,
                    Set.of(hostId, memberId),
                    0L,
                    LocalDateTime.now()
            );

            given(persistence.findById(id)).willReturn(Optional.of(domain));

            // when
            boolean result = sut.join(id, memberId);

            // then
            assertThat(result).isFalse();

            then(persistence)
                    .should()
                    .findById(id);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("채팅방이 없으면 ChatRoomNotFoundException이 발생한다")
        void join_shouldThrow_whenChatRoomNotFound() {
            // given
            given(persistence.findById(id)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.join(id, memberId))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("cache join 실패 시 cache info invalidate 이벤트를 발행하고 true를 반환한다")
        void join_shouldPublishCacheInfoInvalidateEvent_whenCacheJoinFails() {
            // given
            ChatRoom domain = chatRoom();

            given(persistence.findById(id)).willReturn(Optional.of(domain));

            doThrow(new RuntimeException("cache join failed"))
                    .when(cache)
                    .joinMembership(id, memberId);

            // when
            boolean result = sut.join(id, memberId);

            // then
            assertThat(result).isTrue();

            InOrder inOrder = inOrder(
                    persistence,
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(persistence)
                    .findById(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .joinMembership(id, memberId);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
        }
    }

    @Nested
    @DisplayName("leave")
    class LeaveTest {

        @Test
        @DisplayName("마지막 멤버가 나가면 삭제 이벤트를 발행하고 캐시에서 채팅방을 삭제한다")
        void leave_shouldDelete_whenMemberIsLastMember() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    id,
                    hostId,
                    oldTitle,
                    "description",
                    category,
                    Set.of(memberId),
                    0L,
                    LocalDateTime.now()
            );

            given(chatRoomQueryUseCase.getRoom(id)).willReturn(domain);

            // when
            sut.leave(id, memberId);

            // then
            InOrder inOrder = inOrder(
                    chatRoomQueryUseCase,
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(chatRoomQueryUseCase)
                    .getRoom(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .deleteRoom(id, category, oldTitle, domain.getMemberIds());
        }

        @Test
        @DisplayName("멤버가 나가면 이벤트를 발행하고 캐시 멤버십을 제거한다")
        void leave_shouldRemoveMemberPublishEventAndUpdateCache() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    id,
                    hostId,
                    oldTitle,
                    "description",
                    category,
                    Set.of(hostId, memberId),
                    0L,
                    LocalDateTime.now()
            );

            given(chatRoomQueryUseCase.getRoom(id)).willReturn(domain);

            // when
            sut.leave(id, memberId);

            // then
            assertThat(domain.getMemberIds()).doesNotContain(memberId);

            InOrder inOrder = inOrder(
                    chatRoomQueryUseCase,
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(chatRoomQueryUseCase)
                    .getRoom(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .leaveMembership(id, memberId);
        }

        @Test
        @DisplayName("채팅방 멤버가 아니면 아무 작업도 하지 않는다")
        void leave_shouldDoNothing_whenMemberDoesNotExist() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    id,
                    hostId,
                    oldTitle,
                    "description",
                    category,
                    Set.of(hostId),
                    0L,
                    LocalDateTime.now()
            );

            given(chatRoomQueryUseCase.getRoom(id)).willReturn(domain);

            // when
            sut.leave(id, memberId);

            // then
            then(chatRoomQueryUseCase)
                    .should()
                    .getRoom(id);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("채팅방이 없으면 ChatRoomNotFoundException이 발생한다")
        void leave_shouldThrow_whenChatRoomNotFound() {
            // given
            given(chatRoomQueryUseCase.getRoom(id))
                    .willThrow(new ChatRoomNotFoundException(id));

            // when & then
            assertThatThrownBy(() -> sut.leave(id, memberId))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("cache leave 실패 시 cache info invalidate 이벤트를 발행한다")
        void leave_shouldPublishCacheInfoInvalidateEvent_whenCacheLeaveFails() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    id,
                    hostId,
                    oldTitle,
                    "description",
                    category,
                    Set.of(hostId, memberId),
                    0L,
                    LocalDateTime.now()
            );

            given(chatRoomQueryUseCase.getRoom(id)).willReturn(domain);

            doThrow(new RuntimeException("cache leave failed"))
                    .when(cache)
                    .leaveMembership(id, memberId);

            // when
            sut.leave(id, memberId);

            // then
            InOrder inOrder = inOrder(
                    chatRoomQueryUseCase,
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(chatRoomQueryUseCase)
                    .getRoom(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .leaveMembership(id, memberId);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
        }
    }

    @Nested
    @DisplayName("activity")
    class ActivityTest {

        @Test
        @DisplayName("채팅방 활동 이벤트를 발행하고 캐시의 lastReadSeq와 activityScore를 갱신한다")
        void activity_shouldPublishActiveEventAndUpdateCache() {
            // given
            ChatRoomActivityCommand command = new ChatRoomActivityCommand(
                    id,
                    memberId,
                    lastMsgSeq,
                    lastMsgMs
            );

            // when
            sut.activity(command);

            // then
            InOrder inOrder = inOrder(
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .updateLastReadSeq(id, memberId, lastMsgSeq);
            inOrder.verify(cache)
                    .updateActivityScore(id, memberId, lastMsgMs);
        }

        @Test
        @DisplayName("cache activity 실패 시 cache activity invalidate 이벤트를 발행한다")
        void activity_shouldPublishCacheActivityInvalidateEvent_whenCacheActivityFails() {
            // given
            ChatRoomActivityCommand command = new ChatRoomActivityCommand(
                    id,
                    memberId,
                    lastMsgSeq,
                    lastMsgMs
            );

            doThrow(new RuntimeException("cache activity failed"))
                    .when(cache)
                    .updateLastReadSeq(id, memberId, lastMsgSeq);

            // when
            sut.activity(command);

            // then
            InOrder inOrder = inOrder(
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .updateLastReadSeq(id, memberId, lastMsgSeq);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));

            then(cache)
                    .should(never())
                    .updateActivityScore(any(), any(), any());
        }

        @Test
        @DisplayName("activity 이벤트 발행 중 일반 예외가 발생하면 ChatRoomEventPublishException으로 감싼다")
        void activity_shouldWrapUnexpectedOutboxException() {
            // given
            ChatRoomActivityCommand command = new ChatRoomActivityCommand(
                    id,
                    memberId,
                    lastMsgSeq,
                    lastMsgMs
            );

            RuntimeException exception = new RuntimeException("outbox failed");

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.activity(command))
                    .isInstanceOf(ChatRoomEventPublishException.class)
                    .hasCause(exception);

            then(cache)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTest {

        @Test
        @DisplayName("id로 채팅방을 조회한 뒤 삭제 이벤트를 발행하고 캐시에서 삭제한다")
        void delete_shouldFindDomainPublishDeletedEventAndDeleteCache() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    id,
                    hostId,
                    oldTitle,
                    "description",
                    category,
                    Set.of(hostId, memberId),
                    0L,
                    LocalDateTime.now()
            );

            given(persistence.findById(id)).willReturn(Optional.of(domain));

            // when
            sut.delete(id, hostId);

            // then
            InOrder inOrder = inOrder(
                    persistence,
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(persistence)
                    .findById(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .deleteRoom(id, category, oldTitle, domain.getMemberIds());
        }

        @Test
        @DisplayName("채팅방이 없으면 ChatRoomNotFoundException이 발생한다")
        void delete_shouldThrow_whenChatRoomNotFound() {
            // given
            given(persistence.findById(id)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.delete(id, hostId))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            then(outboxEventListPublishPort)
                    .shouldHaveNoInteractions();

            then(cache)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("cache delete 실패 시 cache delete fallback 이벤트를 발행한다")
        void delete_shouldPublishCacheDeleteFallbackEvent_whenCacheDeleteFails() {
            // given
            ChatRoom domain = ChatRoom.rehydrate(
                    id,
                    hostId,
                    oldTitle,
                    "description",
                    category,
                    Set.of(hostId, memberId),
                    0L,
                    LocalDateTime.now()
            );

            given(persistence.findById(id)).willReturn(Optional.of(domain));

            doThrow(new RuntimeException("cache delete failed"))
                    .when(cache)
                    .deleteRoom(id, category, oldTitle, domain.getMemberIds());

            // when
            sut.delete(id, hostId);

            // then
            InOrder inOrder = inOrder(
                    persistence,
                    outboxEventListPublishPort,
                    cache
            );

            inOrder.verify(persistence)
                    .findById(id);
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
            inOrder.verify(cache)
                    .deleteRoom(id, category, oldTitle, domain.getMemberIds());
            inOrder.verify(outboxEventListPublishPort)
                    .publish(any(ChatRoomEventList.class));
        }
    }

    private ChatRoom chatRoom() {
        return chatRoomWithTitle(oldTitle);
    }

    private ChatRoom chatRoomWithTitle(String title) {
        return ChatRoom.rehydrate(
                id,
                hostId,
                title,
                "description",
                category,
                Set.of(hostId),
                0L,
                LocalDateTime.now()
        );
    }
}
