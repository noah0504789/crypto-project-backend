package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.dto.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.event.ChatRoomEventList;
import org.example.chat.chatroom.domain.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chat.common.exception.ChatRoomPersistException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomCommandServiceTest {

    @Mock
    private ChatRoomCachePort cache;

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private OutboxEventListPublishPort outboxEventListPublishPort;

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

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("채팅방을 저장하면 persist 이벤트 발행, 캐시 저장, activity 갱신을 수행한다")
        void save_should_persist_publish_save_cache_and_call_activity() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList eventList = mock(ChatRoomEventList.class);

            when(domain.getId()).thenReturn(id);
            when(domain.getHostId()).thenReturn(hostId);
            when(domain.pullEventList()).thenReturn(eventList);

            doNothing().when(sut).activity(id, hostId, 0L, 0L);

            // when
            sut.save(domain);

            // then
            InOrder inOrder = inOrder(domain, outboxEventListPublishPort, cache, sut);

            inOrder.verify(domain).persist();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(eventList);
            inOrder.verify(cache).save(domain);
            inOrder.verify(sut).activity(id, hostId, 0L, 0L);

            verify(domain, never()).cacheSave();
        }

        @Test
        @DisplayName("캐시 저장이 실패하면 cacheSave 이벤트를 발행하고 activity를 수행한다")
        void save_should_publish_cache_save_event_and_call_activity_even_if_cache_save_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList persistEventList = mock(ChatRoomEventList.class);
            ChatRoomEventList cacheSaveEventList = mock(ChatRoomEventList.class);

            when(domain.getId()).thenReturn(id);
            when(domain.getHostId()).thenReturn(hostId);
            when(domain.pullEventList()).thenReturn(persistEventList, cacheSaveEventList);

            doThrow(new RuntimeException("cache save failed"))
                    .when(cache)
                    .save(domain);

            doNothing().when(sut).activity(id, hostId, 0L, 0L);

            // when & then
            assertDoesNotThrow(() -> sut.save(domain));

            InOrder inOrder = inOrder(domain, outboxEventListPublishPort, cache, sut);

            inOrder.verify(domain).persist();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(persistEventList);

            inOrder.verify(cache).save(domain);

            inOrder.verify(domain).cacheSave();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(cacheSaveEventList);

            inOrder.verify(sut).activity(id, hostId, 0L, 0L);
        }

        @Test
        @DisplayName("도메인 persist가 실패하면 이벤트 발행, 캐시 저장, activity를 수행하지 않는다")
        void save_should_throw_if_domain_persist_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);

            doThrow(new RuntimeException("persist failed"))
                    .when(domain)
                    .persist();

            // when & then
            assertThrows(RuntimeException.class, () -> sut.save(domain));

            verify(domain).persist();
            verify(domain, never()).pullEventList();
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).save(any());
            verify(sut, never()).activity(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("persist 이벤트 발행이 실패하면 캐시 저장과 activity를 수행하지 않는다")
        void save_should_throw_if_persist_event_publish_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList eventList = mock(ChatRoomEventList.class);

            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(eventList);

            doThrow(new RuntimeException("outbox publish failed"))
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            // when & then
            assertThrows(ChatRoomPersistException.class, () -> sut.save(domain));

            verify(domain).persist();
            verify(domain).pullEventList();
            verify(outboxEventListPublishPort).publish(eventList);
            verify(cache, never()).save(any());
            verify(sut, never()).activity(anyString(), anyString(), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("activity")
    class ActivityTest {

        @Test
        @DisplayName("활동 정보를 갱신하면 active 이벤트 발행 후 lastRead와 recentScore 캐시를 갱신한다")
        void activity_should_publish_active_event_and_update_cache() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList eventList = mock(ChatRoomEventList.class);

            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(eventList);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(id)).thenReturn(domain);

                // when
                sut.activity(id, memberId, lastMsgSeq, lastMsgMs);

                // then
                InOrder inOrder = inOrder(domain, outboxEventListPublishPort, cache);

                inOrder.verify(domain).active(memberId, lastMsgSeq, lastMsgMs);
                inOrder.verify(domain).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);
                inOrder.verify(cache).updateLastRead(id, memberId, lastMsgSeq);
                inOrder.verify(cache).updateRecentScore(id, memberId, lastMsgMs);

                verify(domain, never()).cacheActivityInvalidate(anyString());
            }
        }

        @Test
        @DisplayName("lastRead 캐시 갱신이 실패하면 recentScore를 건너뛰고 activity invalidate 이벤트를 발행한다")
        void activity_should_publish_invalidate_event_if_cache_updateLastRead_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList activeEventList = mock(ChatRoomEventList.class);
            ChatRoomEventList invalidateEventList = mock(ChatRoomEventList.class);

            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(activeEventList, invalidateEventList);

            doThrow(new RuntimeException("cache updateLastRead failed"))
                    .when(cache)
                    .updateLastRead(id, memberId, lastMsgSeq);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(id)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> sut.activity(id, memberId, lastMsgSeq, lastMsgMs));

                InOrder inOrder = inOrder(domain, outboxEventListPublishPort, cache);

                inOrder.verify(domain).active(memberId, lastMsgSeq, lastMsgMs);
                inOrder.verify(domain).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(activeEventList);

                inOrder.verify(cache).updateLastRead(id, memberId, lastMsgSeq);

                inOrder.verify(domain).cacheActivityInvalidate(memberId);
                inOrder.verify(domain).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(invalidateEventList);

                verify(cache, never()).updateRecentScore(id, memberId, lastMsgMs);
            }
        }

        @Test
        @DisplayName("recentScore 캐시 갱신이 실패하면 activity invalidate 이벤트를 발행한다")
        void activity_should_publish_invalidate_event_if_cache_updateRecentScore_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList activeEventList = mock(ChatRoomEventList.class);
            ChatRoomEventList invalidateEventList = mock(ChatRoomEventList.class);

            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(activeEventList, invalidateEventList);

            doThrow(new RuntimeException("cache updateRecentScore failed"))
                    .when(cache)
                    .updateRecentScore(id, memberId, lastMsgMs);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(id)).thenReturn(domain);

                // when & then
                assertDoesNotThrow(() -> sut.activity(id, memberId, lastMsgSeq, lastMsgMs));

                InOrder inOrder = inOrder(domain, outboxEventListPublishPort, cache);

                inOrder.verify(domain).active(memberId, lastMsgSeq, lastMsgMs);
                inOrder.verify(domain).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(activeEventList);

                inOrder.verify(cache).updateLastRead(id, memberId, lastMsgSeq);
                inOrder.verify(cache).updateRecentScore(id, memberId, lastMsgMs);

                inOrder.verify(domain).cacheActivityInvalidate(memberId);
                inOrder.verify(domain).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(invalidateEventList);
            }
        }

        @Test
        @DisplayName("도메인 active가 실패하면 이벤트 발행과 캐시 갱신을 수행하지 않는다")
        void activity_should_throw_if_domain_active_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);

            doThrow(new RuntimeException("domain active failed"))
                    .when(domain)
                    .active(memberId, lastMsgSeq, lastMsgMs);

            try (MockedStatic<ChatRoom> mockedStatic = Mockito.mockStatic(ChatRoom.class)) {
                mockedStatic.when(() -> ChatRoom.ofId(id)).thenReturn(domain);

                // when & then
                assertThrows(RuntimeException.class, () -> sut.activity(id, memberId, lastMsgSeq, lastMsgMs));

                verify(domain).active(memberId, lastMsgSeq, lastMsgMs);
                verify(domain, never()).pullEventList();
                verify(outboxEventListPublishPort, never()).publish(any());
                verify(cache, never()).updateLastRead(anyString(), anyString(), anyLong());
                verify(cache, never()).updateRecentScore(anyString(), anyString(), anyLong());
            }
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTest {

        @Test
        @DisplayName("채팅방을 수정하면 update 이벤트 발행 후 캐시 update를 수행한다")
        void update_should_publish_update_event_and_update_cache() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList eventList = mock(ChatRoomEventList.class);

            ChatRoomUpdateCommand command = new ChatRoomUpdateCommand("수정된 제목", "수정된 설명", ChatRoomCategory.FREE);
            ChatRoomUpdatedPayload payload = command.toPayload();
            Map<String, Object> updated = payload.toUpdateMap();

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.getId()).thenReturn(id);
            when(domain.getTitle()).thenReturn(oldTitle);
            when(domain.pullEventList()).thenReturn(eventList);

            // when
            sut.update(id, command);

            // then
            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);
            inOrder.verify(domain).getTitle();
            inOrder.verify(domain).update(payload);
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(eventList);
            inOrder.verify(cache).update(id, updated, oldTitle);

            verify(domain, never()).cacheUpdate(anyString());
        }

        @Test
        @DisplayName("캐시 update가 실패하면 cacheUpdate 이벤트를 발행하고 예외를 삼킨다")
        void update_should_publish_cache_update_event_if_cache_update_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList updateEventList = mock(ChatRoomEventList.class);
            ChatRoomEventList cacheUpdateEventList = mock(ChatRoomEventList.class);

            ChatRoomUpdateCommand command = new ChatRoomUpdateCommand("수정된 제목", "수정된 설명", ChatRoomCategory.FREE);
            ChatRoomUpdatedPayload payload = command.toPayload();
            Map<String, Object> updated = payload.toUpdateMap();

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.getId()).thenReturn(id);
            when(domain.getTitle()).thenReturn(oldTitle);
            when(domain.pullEventList()).thenReturn(updateEventList, cacheUpdateEventList);

            doThrow(new RuntimeException("cache update failed"))
                    .when(cache)
                    .update(id, updated, oldTitle);

            // when & then
            assertDoesNotThrow(() -> sut.update(id, command));

            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);
            inOrder.verify(domain).getTitle();
            inOrder.verify(domain).update(payload);
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(updateEventList);

            inOrder.verify(cache).update(id, updated, oldTitle);

            inOrder.verify(domain).cacheUpdate(oldTitle);
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(cacheUpdateEventList);
        }

        @Test
        @DisplayName("수정할 채팅방이 없으면 ChatRoomNotFoundException을 던지고 이벤트 발행과 캐시 갱신을 수행하지 않는다")
        void update_should_throw_if_domain_not_found() {
            // given
            ChatRoomUpdateCommand command = new ChatRoomUpdateCommand("수정된 제목", "수정된 설명", ChatRoomCategory.FREE);

            when(persistence.findById(id)).thenReturn(Optional.empty());

            // when & then
            assertThrows(ChatRoomNotFoundException.class, () -> sut.update(id, command));

            verify(persistence).findById(id);
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).update(anyString(), anyMap(), anyString());
        }

        @Test
        @DisplayName("도메인 update가 실패하면 이벤트 발행과 캐시 update를 수행하지 않는다")
        void update_should_throw_if_domain_update_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);

            ChatRoomUpdateCommand command = new ChatRoomUpdateCommand("수정된 제목", "수정된 설명", ChatRoomCategory.FREE);
            ChatRoomUpdatedPayload payload = command.toPayload();

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.getTitle()).thenReturn(oldTitle);

            doThrow(new RuntimeException("domain update failed"))
                    .when(domain)
                    .update(payload);

            // when & then
            assertThrows(RuntimeException.class, () -> sut.update(id, command));

            verify(persistence).findById(id);
            verify(domain).getTitle();
            verify(domain).update(payload);
            verify(domain, never()).pullEventList();
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).update(anyString(), anyMap(), anyString());
            verify(domain, never()).cacheUpdate(anyString());
        }
    }

    @Nested
    @DisplayName("join")
    class JoinTest {

        @Test
        @DisplayName("새 멤버가 입장하면 join 이벤트 발행 후 캐시에 join을 반영한다")
        void join_should_publish_join_event_and_update_cache() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList eventList = mock(ChatRoomEventList.class);

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.addMember(memberId)).thenReturn(true);
            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(eventList);

            // when
            boolean result = sut.join(id, memberId);

            // then
            assertThat(result).isTrue();

            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);
            inOrder.verify(domain).addMember(memberId);
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(eventList);
            inOrder.verify(cache).join(id, memberId);

            verify(domain, never()).cacheInfoInvalidate();
        }

        @Test
        @DisplayName("이미 참여한 멤버면 이벤트 발행과 캐시 join을 수행하지 않고 false를 반환한다")
        void join_should_return_false_if_already_joined() {
            // given
            ChatRoom domain = mock(ChatRoom.class);

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.addMember(memberId)).thenReturn(false);

            // when
            boolean result = sut.join(id, memberId);

            // then
            assertThat(result).isFalse();

            verify(persistence).findById(id);
            verify(domain).addMember(memberId);
            verify(domain, never()).pullEventList();
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).join(anyString(), anyString());
            verify(domain, never()).cacheInfoInvalidate();
        }

        @Test
        @DisplayName("캐시 join이 실패하면 cacheInfoInvalidate 이벤트를 발행하고 true를 반환한다")
        void join_should_publish_cache_info_invalidate_event_if_cache_join_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList joinEventList = mock(ChatRoomEventList.class);
            ChatRoomEventList invalidateEventList = mock(ChatRoomEventList.class);

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.addMember(memberId)).thenReturn(true);
            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(joinEventList, invalidateEventList);

            doThrow(new RuntimeException("cache join failed"))
                    .when(cache)
                    .join(id, memberId);

            // when
            boolean result = sut.join(id, memberId);

            // then
            assertThat(result).isTrue();

            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);
            inOrder.verify(domain).addMember(memberId);
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(joinEventList);

            inOrder.verify(cache).join(id, memberId);

            inOrder.verify(domain).cacheInfoInvalidate();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(invalidateEventList);
        }

        @Test
        @DisplayName("입장할 채팅방이 없으면 ChatRoomNotFoundException을 던진다")
        void join_should_throw_if_domain_not_found() {
            // given
            when(persistence.findById(id)).thenReturn(Optional.empty());

            // when & then
            assertThrows(ChatRoomNotFoundException.class, () -> sut.join(id, memberId));

            verify(persistence).findById(id);
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).join(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("leave")
    class LeaveTest {

        @Test
        @DisplayName("마지막 멤버가 아니면 leave 이벤트 발행 후 캐시에 leave를 반영한다")
        void leave_should_publish_leave_event_and_update_cache_when_not_last_member() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList eventList = mock(ChatRoomEventList.class);

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.isLastMember(memberId)).thenReturn(false);
            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(eventList);

            // when
            sut.leave(id, memberId);

            // then
            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);
            inOrder.verify(domain).isLastMember(memberId);
            inOrder.verify(domain).removeMember(memberId);
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(eventList);
            inOrder.verify(cache).leave(id, memberId);

            verify(domain, never()).delete();
            verify(cache, never()).delete(anyString(), any(), anyString(), anySet());
            verify(domain, never()).cacheInfoInvalidate();
        }

        @Test
        @DisplayName("마지막 멤버가 나가면 leave 대신 delete 이벤트를 발행하고 캐시 delete를 수행한다")
        void leave_should_delete_room_if_member_is_last_member() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList deleteEventList = mock(ChatRoomEventList.class);
            Set<String> memberIds = Set.of(memberId);

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.isLastMember(memberId)).thenReturn(true);
            when(domain.getId()).thenReturn(id);
            when(domain.getCategory()).thenReturn(category);
            when(domain.getTitle()).thenReturn(newTitle);
            when(domain.getMemberIds()).thenReturn(memberIds);
            when(domain.pullEventList()).thenReturn(deleteEventList);

            // when
            sut.leave(id, memberId);

            // then
            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);
            inOrder.verify(domain).isLastMember(memberId);

            inOrder.verify(domain).getId();
            inOrder.verify(domain).getCategory();
            inOrder.verify(domain).getTitle();
            inOrder.verify(domain).getMemberIds();

            inOrder.verify(domain).delete();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(deleteEventList);
            inOrder.verify(cache).delete(id, category, newTitle, memberIds);

            verify(domain, never()).removeMember(anyString());
            verify(cache, never()).leave(anyString(), anyString());
        }

        @Test
        @DisplayName("캐시 leave가 실패하면 cacheInfoInvalidate 이벤트를 발행하고 예외를 삼킨다")
        void leave_should_publish_cache_info_invalidate_event_if_cache_leave_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList leaveEventList = mock(ChatRoomEventList.class);
            ChatRoomEventList invalidateEventList = mock(ChatRoomEventList.class);

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.isLastMember(memberId)).thenReturn(false);
            when(domain.getId()).thenReturn(id);
            when(domain.pullEventList()).thenReturn(leaveEventList, invalidateEventList);

            doThrow(new RuntimeException("cache leave failed"))
                    .when(cache)
                    .leave(id, memberId);

            // when & then
            assertDoesNotThrow(() -> sut.leave(id, memberId));

            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);
            inOrder.verify(domain).isLastMember(memberId);
            inOrder.verify(domain).removeMember(memberId);
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(leaveEventList);

            inOrder.verify(cache).leave(id, memberId);

            inOrder.verify(domain).cacheInfoInvalidate();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(invalidateEventList);

            verify(domain, never()).delete();
            verify(cache, never()).delete(anyString(), any(), anyString(), anySet());
        }

        @Test
        @DisplayName("나갈 채팅방이 없으면 ChatRoomNotFoundException을 던진다")
        void leave_should_throw_if_domain_not_found() {
            // given
            when(persistence.findById(id)).thenReturn(Optional.empty());

            // when & then
            assertThrows(ChatRoomNotFoundException.class, () -> sut.leave(id, memberId));

            verify(persistence).findById(id);
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).leave(anyString(), anyString());
            verify(cache, never()).delete(anyString(), any(), anyString(), anySet());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTest {

        @Test
        @DisplayName("채팅방을 삭제하면 delete 이벤트 발행 후 캐시 delete를 수행한다")
        void delete_should_publish_delete_event_and_cache_delete() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList eventList = mock(ChatRoomEventList.class);
            Set<String> memberIds = Set.of("member-1", "member-2");

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.getId()).thenReturn(id);
            when(domain.getCategory()).thenReturn(category);
            when(domain.getTitle()).thenReturn(newTitle);
            when(domain.getMemberIds()).thenReturn(memberIds);
            when(domain.pullEventList()).thenReturn(eventList);

            // when
            sut.delete(id);

            // then
            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);

            inOrder.verify(domain).getId();
            inOrder.verify(domain).getCategory();
            inOrder.verify(domain).getTitle();
            inOrder.verify(domain).getMemberIds();

            inOrder.verify(domain).delete();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(eventList);
            inOrder.verify(cache).delete(id, category, newTitle, memberIds);

            verify(domain, never()).cacheDelete();
        }

        @Test
        @DisplayName("캐시 delete가 실패하면 cacheDelete 이벤트를 발행하고 예외를 삼킨다")
        void delete_should_publish_cache_delete_event_if_cache_delete_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            ChatRoomEventList deleteEventList = mock(ChatRoomEventList.class);
            ChatRoomEventList cacheDeleteEventList = mock(ChatRoomEventList.class);
            Set<String> memberIds = Set.of("member-1", "member-2");

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.getId()).thenReturn(id);
            when(domain.getCategory()).thenReturn(category);
            when(domain.getTitle()).thenReturn(newTitle);
            when(domain.getMemberIds()).thenReturn(memberIds);
            when(domain.pullEventList()).thenReturn(deleteEventList, cacheDeleteEventList);

            doThrow(new RuntimeException("cache delete failed"))
                    .when(cache)
                    .delete(id, category, newTitle, memberIds);

            // when & then
            assertDoesNotThrow(() -> sut.delete(id));

            InOrder inOrder = inOrder(persistence, domain, outboxEventListPublishPort, cache);

            inOrder.verify(persistence).findById(id);

            inOrder.verify(domain).delete();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(deleteEventList);

            inOrder.verify(cache).delete(id, category, newTitle, memberIds);

            inOrder.verify(domain).cacheDelete();
            inOrder.verify(domain).pullEventList();
            inOrder.verify(outboxEventListPublishPort).publish(cacheDeleteEventList);
        }

        @Test
        @DisplayName("삭제할 채팅방이 없으면 ChatRoomNotFoundException을 던지고 이벤트 발행과 캐시 delete를 수행하지 않는다")
        void delete_should_throw_if_domain_not_found() {
            // given
            when(persistence.findById(id)).thenReturn(Optional.empty());

            // when & then
            assertThrows(ChatRoomNotFoundException.class, () -> sut.delete(id));

            verify(persistence).findById(id);
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).delete(anyString(), any(), anyString(), anySet());
        }

        @Test
        @DisplayName("도메인 delete가 실패하면 이벤트 발행과 캐시 delete를 수행하지 않는다")
        void delete_should_throw_if_domain_delete_fails() {
            // given
            ChatRoom domain = mock(ChatRoom.class);
            Set<String> memberIds = Set.of("member-1", "member-2");

            when(persistence.findById(id)).thenReturn(Optional.of(domain));
            when(domain.getId()).thenReturn(id);
            when(domain.getCategory()).thenReturn(category);
            when(domain.getTitle()).thenReturn(newTitle);
            when(domain.getMemberIds()).thenReturn(memberIds);

            doThrow(new RuntimeException("domain delete failed"))
                    .when(domain)
                    .delete();

            // when & then
            assertThrows(RuntimeException.class, () -> sut.delete(id));

            verify(persistence).findById(id);
            verify(domain).delete();
            verify(domain, never()).pullEventList();
            verify(outboxEventListPublishPort, never()).publish(any());
            verify(cache, never()).delete(anyString(), any(), anyString(), anySet());
        }
    }
}