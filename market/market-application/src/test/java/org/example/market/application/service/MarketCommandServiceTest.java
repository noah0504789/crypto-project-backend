package org.example.market.application.service;

import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.application.service.command.ChangeMarketsCommand.UpdateMarketCommand;
import org.example.market.application.service.command.ChangeMarketsCommand.CreateMarketCommand;
import org.example.market.application.exception.MarketPersistException;
import org.example.market.domain.event.MarketEventList;
import org.example.market.domain.model.Market;
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
class MarketCommandServiceTest {

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

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                // when
                sut.changeMarkets(command);

                // then
                verify(marketPersistencePort, never()).deleteMarketsByIds(any());
                verify(marketPersistencePort, never()).updateMarkets(any());
                verify(marketPersistencePort, never()).createMarkets(any());
                verify(outboxEventListPublishPort, never()).publish(any());

                mockedStatic.verify(Market::eventSource, never());
            }
        }

        @Test
        @DisplayName("마켓 변경 요청이 있으면 삭제, 수정, 생성 순서로 저장한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldApplyChangesAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            List<Long> deleteIds = List.of(1L, 2L);
            List<UpdateMarketCommand> updates = List.of(mock(UpdateMarketCommand.class));
            List<CreateMarketCommand> creates = List.of(mock(ChangeMarketsCommand.CreateMarketCommand.class));

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(true);
            given(command.hasCreates()).willReturn(true);
            given(command.deleteIds()).willReturn(deleteIds);
            given(command.updates()).willReturn(updates);
            given(command.creates()).willReturn(creates);

            given(market.pullEventList()).willReturn(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when
                sut.changeMarkets(command);

                // then
                InOrder inOrder = inOrder(
                        marketPersistencePort,
                        market,
                        outboxEventListPublishPort
                );

                inOrder.verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
                inOrder.verify(marketPersistencePort).updateMarkets(updates);
                inOrder.verify(marketPersistencePort).createMarkets(creates);
                inOrder.verify(market).catalogChanged();
                inOrder.verify(market).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("삭제 요청만 있으면 삭제만 수행한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldDeleteOnlyAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            List<Long> deleteIds = List.of(1L, 2L);

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(false);
            given(command.deleteIds()).willReturn(deleteIds);

            given(market.pullEventList()).willReturn(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when
                sut.changeMarkets(command);

                // then
                verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
                verify(marketPersistencePort, never()).updateMarkets(any());
                verify(marketPersistencePort, never()).createMarkets(any());

                verify(market).catalogChanged();
                verify(market).pullEventList();
                verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("수정 요청만 있으면 수정만 수행한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldUpdateOnlyAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            List<UpdateMarketCommand> updates = List.of(mock(UpdateMarketCommand.class));

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(false);
            given(command.hasUpdates()).willReturn(true);
            given(command.hasCreates()).willReturn(false);
            given(command.updates()).willReturn(updates);

            given(market.pullEventList()).willReturn(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when
                sut.changeMarkets(command);

                // then
                verify(marketPersistencePort, never()).deleteMarketsByIds(any());
                verify(marketPersistencePort).updateMarkets(updates);
                verify(marketPersistencePort, never()).createMarkets(any());

                verify(market).catalogChanged();
                verify(market).pullEventList();
                verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("생성 요청만 있으면 생성만 수행한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_shouldCreateOnlyAndPublishMarketChangedEvent() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            List<CreateMarketCommand> creates = List.of(mock(CreateMarketCommand.class));

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(false);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(true);
            given(command.creates()).willReturn(creates);

            given(market.pullEventList()).willReturn(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when
                sut.changeMarkets(command);

                // then
                verify(marketPersistencePort, never()).deleteMarketsByIds(any());
                verify(marketPersistencePort, never()).updateMarkets(any());
                verify(marketPersistencePort).createMarkets(creates);

                verify(market).catalogChanged();
                verify(market).pullEventList();
                verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("Outbox 일시 장애가 발생하면 TemporaryOutboxPersistenceException을 그대로 전파한다")
        void changeMarkets_shouldRethrowTemporaryOutboxException() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

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

            given(market.pullEventList()).willReturn(eventList);

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when & then
                assertThatThrownBy(() -> sut.changeMarkets(command))
                        .isSameAs(exception);

                InOrder inOrder = inOrder(
                        marketPersistencePort,
                        market,
                        outboxEventListPublishPort
                );

                inOrder.verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
                inOrder.verify(market).catalogChanged();
                inOrder.verify(market).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("Outbox 일반 장애가 발생하면 MarketPersistException으로 감싸서 전파한다")
        void changeMarkets_shouldWrapUnexpectedOutboxException() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            List<Long> deleteIds = List.of(1L);

            RuntimeException exception = new RuntimeException("outbox publish failed");

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(false);
            given(command.deleteIds()).willReturn(deleteIds);

            given(market.pullEventList()).willReturn(eventList);

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when & then
                assertThatThrownBy(() -> sut.changeMarkets(command))
                        .isInstanceOf(MarketPersistException.class)
                        .hasMessageContaining("failed to publish market changed event")
                        .hasCause(exception);

                InOrder inOrder = inOrder(
                        marketPersistencePort,
                        market,
                        outboxEventListPublishPort
                );

                inOrder.verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
                inOrder.verify(market).catalogChanged();
                inOrder.verify(market).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("마켓 변경 저장이 실패하면 이벤트를 생성하거나 발행하지 않는다")
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

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                // when & then
                assertThatThrownBy(() -> sut.changeMarkets(command))
                        .isSameAs(exception);

                verify(marketPersistencePort).deleteMarketsByIds(deleteIds);
                verify(marketPersistencePort, never()).updateMarkets(any());
                verify(marketPersistencePort, never()).createMarkets(any());
                verify(outboxEventListPublishPort, never()).publish(any());

                mockedStatic.verify(Market::eventSource, never());
            }
        }
    }
}