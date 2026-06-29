package org.example.market.application.service;

import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.common.exception.MarketPersistException;
import org.example.market.domain.event.MarketEventList;
import org.example.market.domain.model.Market;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

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
        void changeMarkets_should_do_nothing_when_command_is_empty() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            given(command.isEmpty()).willReturn(true);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                // when
                sut.changeMarkets(command);

                // then
                verify(marketPersistencePort, never()).changeMarkets(any());
                verify(outboxEventListPublishPort, never()).publish(any());

                mockedStatic.verify(Market::eventSource, never());
            }
        }

        @Test
        @DisplayName("마켓을 변경하면 변경 내용을 저장한 뒤 마켓 변경 이벤트를 발행한다")
        void changeMarkets_should_change_markets_and_publish_market_changed_event() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            given(command.isEmpty()).willReturn(false);
            given(market.pullEventList()).willReturn(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when
                sut.changeMarkets(command);

                // then
                InOrder inOrder = inOrder(marketPersistencePort, market, outboxEventListPublishPort);

                inOrder.verify(marketPersistencePort).changeMarkets(command);
                inOrder.verify(market).catalogChanged();
                inOrder.verify(market).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("Outbox 일시 장애가 발생하면 TemporaryOutboxPersistenceException을 그대로 전파한다")
        void changeMarkets_should_rethrow_temporary_outbox_exception() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            TemporaryOutboxPersistenceException exception =
                    new TemporaryOutboxPersistenceException(
                            "temporary outbox failure",
                            new RuntimeException("temporary")
                    );

            given(command.isEmpty()).willReturn(false);
            given(market.pullEventList()).willReturn(eventList);

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                mockedStatic.when(Market::eventSource).thenReturn(market);

                // when & then
                assertThatThrownBy(() -> sut.changeMarkets(command))
                        .isSameAs(exception);

                InOrder inOrder = inOrder(marketPersistencePort, market, outboxEventListPublishPort);

                inOrder.verify(marketPersistencePort).changeMarkets(command);
                inOrder.verify(market).catalogChanged();
                inOrder.verify(market).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("Outbox 일반 장애가 발생하면 MarketPersistException으로 감싸서 전파한다")
        void changeMarkets_should_wrap_unexpected_outbox_exception() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);
            Market market = mock(Market.class);
            MarketEventList eventList = mock(MarketEventList.class);

            RuntimeException exception = new RuntimeException("outbox publish failed");

            given(command.isEmpty()).willReturn(false);
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

                InOrder inOrder = inOrder(marketPersistencePort, market, outboxEventListPublishPort);

                inOrder.verify(marketPersistencePort).changeMarkets(command);
                inOrder.verify(market).catalogChanged();
                inOrder.verify(market).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);

                mockedStatic.verify(Market::eventSource);
            }
        }

        @Test
        @DisplayName("마켓 변경 저장이 실패하면 이벤트를 생성하거나 발행하지 않는다")
        void changeMarkets_should_not_publish_event_when_persistence_fails() {
            // given
            ChangeMarketsCommand command = mock(ChangeMarketsCommand.class);

            RuntimeException exception = new RuntimeException("market persistence failed");

            given(command.isEmpty()).willReturn(false);

            doThrow(exception)
                    .when(marketPersistencePort)
                    .changeMarkets(command);

            try (MockedStatic<Market> mockedStatic = Mockito.mockStatic(Market.class)) {
                // when & then
                assertThatThrownBy(() -> sut.changeMarkets(command))
                        .isSameAs(exception);

                verify(marketPersistencePort).changeMarkets(command);
                verify(outboxEventListPublishPort, never()).publish(any());

                mockedStatic.verify(Market::eventSource, never());
            }
        }
    }
}