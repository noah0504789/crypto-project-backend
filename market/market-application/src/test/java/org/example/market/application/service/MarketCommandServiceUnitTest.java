package org.example.market.application.service;

import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.application.exception.MarketPersistException;
import org.example.market.application.event.MarketEventList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketCommandServiceUnitTest {

    @Mock
    private MarketPersistencePort marketPersistencePort;

    @Mock
    private OutboxEventListPublishPort outboxEventListPublishPort;

    @InjectMocks
    private MarketCommandService sut;

    @Nested
    @DisplayName("changeMarkets")
    class ChangeMarketsTest {

        @Test
        @DisplayName("변경할 마켓이 없으면 아무 작업도 하지 않는다")
        void changeMarkets_shouldDoNothingWhenCommandIsEmpty() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            given(command.isEmpty()).willReturn(true);

            // when
            sut.changeMarkets(command);

            // then
            verify(marketPersistencePort, never()).deleteMarketsByIds(any());
            verify(marketPersistencePort, never()).updateMarkets(any());
            verify(marketPersistencePort, never()).createMarkets(any());
            verify(outboxEventListPublishPort, never()).publish(any());
        }

        @Test
        @DisplayName("마켓 변경 요청이 있으면 삭제, 수정, 생성 순서로 저장한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldApplyChangesAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            List<Long> deleteIds = List.of(1L, 2L);
            List<ChangeMarketsCommand.UpdateMarketCommand> updates =
                    List.of(mock(ChangeMarketsCommand.UpdateMarketCommand.class));
            List<ChangeMarketsCommand.CreateMarketCommand> creates =
                    List.of(mock(ChangeMarketsCommand.CreateMarketCommand.class));

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(true);
            given(command.hasCreates()).willReturn(true);
            given(command.deleteIds()).willReturn(deleteIds);
            given(command.updates()).willReturn(updates);
            given(command.creates()).willReturn(creates);

            // when
            sut.changeMarkets(command);

            // then
            InOrder inOrder = inOrder(
                    marketPersistencePort,
                    outboxEventListPublishPort
            );

            inOrder.verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
            inOrder.verify(marketPersistencePort).updateMarkets(updates);
            inOrder.verify(marketPersistencePort).createMarkets(creates);
            inOrder.verify(outboxEventListPublishPort).publish(any(MarketEventList.class));
        }

        @Test
        @DisplayName("삭제 요청만 있으면 삭제만 수행한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldDeleteOnlyAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            List<Long> deleteIds = List.of(1L, 2L);

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(false);
            given(command.deleteIds()).willReturn(deleteIds);

            // when
            sut.changeMarkets(command);

            // then
            verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
            verify(marketPersistencePort, never()).updateMarkets(any());
            verify(marketPersistencePort, never()).createMarkets(any());
            verify(outboxEventListPublishPort).publish(any(MarketEventList.class));
        }

        @Test
        @DisplayName("수정 요청만 있으면 수정만 수행한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldUpdateOnlyAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            List<ChangeMarketsCommand.UpdateMarketCommand> updates =
                    List.of(mock(ChangeMarketsCommand.UpdateMarketCommand.class));

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(false);
            given(command.hasUpdates()).willReturn(true);
            given(command.hasCreates()).willReturn(false);
            given(command.updates()).willReturn(updates);

            // when
            sut.changeMarkets(command);

            // then
            verify(marketPersistencePort, never()).deleteMarketsByIds(any());
            verify(marketPersistencePort).updateMarkets(updates);
            verify(marketPersistencePort, never()).createMarkets(any());
            verify(outboxEventListPublishPort).publish(any(MarketEventList.class));
        }

        @Test
        @DisplayName("생성 요청만 있으면 생성만 수행한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldCreateOnlyAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            List<ChangeMarketsCommand.CreateMarketCommand> creates =
                    List.of(mock(ChangeMarketsCommand.CreateMarketCommand.class));

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(false);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(true);
            given(command.creates()).willReturn(creates);

            // when
            sut.changeMarkets(command);

            // then
            verify(marketPersistencePort, never()).deleteMarketsByIds(any());
            verify(marketPersistencePort, never()).updateMarkets(any());
            verify(marketPersistencePort).createMarkets(creates);
            verify(outboxEventListPublishPort).publish(any(MarketEventList.class));
        }

        @Test
        @DisplayName("Outbox 일시 장애가 발생하면 TemporaryOutboxPersistenceException을 그대로 전파한다")
        void changeMarkets_shouldRethrowTemporaryOutboxException() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            List<Long> deleteIds = List.of(1L);

            TemporaryOutboxPersistenceException exception =
                    new TemporaryOutboxPersistenceException(
                            "temporary outbox failure",
                            new RuntimeException("temporary")
                    );

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(false);
            given(command.deleteIds()).willReturn(deleteIds);

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(MarketEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.changeMarkets(command))
                    .isSameAs(exception);

            InOrder inOrder = inOrder(
                    marketPersistencePort,
                    outboxEventListPublishPort
            );

            inOrder.verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
            inOrder.verify(outboxEventListPublishPort).publish(any(MarketEventList.class));
        }

        @Test
        @DisplayName("Outbox 일반 장애가 발생하면 MarketPersistException으로 감싸서 전파한다")
        void changeMarkets_shouldWrapUnexpectedOutboxException() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            List<Long> deleteIds = List.of(1L);

            RuntimeException exception = new RuntimeException("outbox publish failed");

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(false);
            given(command.deleteIds()).willReturn(deleteIds);

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(any(MarketEventList.class));

            // when & then
            assertThatThrownBy(() -> sut.changeMarkets(command))
                    .isInstanceOf(MarketPersistException.class)
                    .hasMessageContaining("failed to publish market changed event")
                    .hasCause(exception);

            InOrder inOrder = inOrder(
                    marketPersistencePort,
                    outboxEventListPublishPort
            );

            inOrder.verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
            inOrder.verify(outboxEventListPublishPort).publish(any(MarketEventList.class));
        }

        @Test
        @DisplayName("마켓 변경 저장이 실패하면 이벤트를 발행하지 않는다")
        void changeMarkets_shouldNotPublishEventWhenPersistenceFails() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            List<Long> deleteIds = List.of(1L);
            RuntimeException exception = new RuntimeException("market persistence failed");

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.deleteIds()).willReturn(deleteIds);

            doThrow(exception)
                    .when(marketPersistencePort)
                    .deleteMarketsByIds(deleteIds);

            // when & then
            assertThatThrownBy(() -> sut.changeMarkets(command))
                    .isSameAs(exception);

            verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
            verify(marketPersistencePort, never()).updateMarkets(any());
            verify(marketPersistencePort, never()).createMarkets(any());
            verify(outboxEventListPublishPort, never()).publish(any());
        }
    }
}