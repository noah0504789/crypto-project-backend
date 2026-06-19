package org.example.market.adapter.out.persistence;

import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.application.service.command.ChangeMarketsCommand.CreateMarketCommand;
import org.example.market.application.service.command.ChangeMarketsCommand.UpdateMarketCommand;
import org.example.market.application.service.command.ChangeMarketsCommand.DeleteMarketCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaMarketAdapterTest {

    @Mock
    private JpaMarketRepository marketRepository;

    @InjectMocks
    private JpaMarketAdapter sut;

    @Test
    @DisplayName("changeMarkets는 command가 비어 있으면 아무 작업도 하지 않는다")
    void changeMarkets_whenCommandIsEmpty_doesNothing() {
        ChangeMarketsCommand command = new ChangeMarketsCommand(
                List.of(),
                List.of(),
                List.of()
        );

        sut.changeMarkets(command);

        verifyNoInteractions(marketRepository);
    }

    @Test
    @DisplayName("changeMarkets는 create 명령이 있으면 JpaMarket을 생성해 저장한다")
    void changeMarkets_whenCreatesExist_savesCreatedMarkets() {
        ChangeMarketsCommand command = new ChangeMarketsCommand(
                List.of(
                        new CreateMarketCommand(
                                "KRW-BTC",
                                "BTC",
                                "비트코인",
                                "Bitcoin",
                                true
                        ),
                        new CreateMarketCommand(
                                "KRW-ETH",
                                "ETH",
                                "이더리움",
                                "Ethereum",
                                true
                        )
                ),
                List.of(),
                List.of()
        );

        sut.changeMarkets(command);

        ArgumentCaptor<List<JpaMarket>> captor = ArgumentCaptor.forClass(List.class);

        verify(marketRepository).saveAll(captor.capture());

        List<JpaMarket> savedMarkets = captor.getValue();

        assertThat(savedMarkets).hasSize(2);

        assertThat(savedMarkets)
                .extracting(JpaMarket::getMarketCode)
                .containsExactly("KRW-BTC", "KRW-ETH");

        assertThat(savedMarkets)
                .extracting(JpaMarket::getSymbol)
                .containsExactly("BTC", "ETH");

        assertThat(savedMarkets)
                .extracting(JpaMarket::getKoreanName)
                .containsExactly("비트코인", "이더리움");

        assertThat(savedMarkets)
                .extracting(JpaMarket::getEnglishName)
                .containsExactly("Bitcoin", "Ethereum");

        assertThat(savedMarkets)
                .extracting(JpaMarket::isEnabled)
                .containsExactly(true, true);
    }

    @Test
    @DisplayName("changeMarkets는 update 명령이 있으면 기존 JpaMarket을 조회해 값을 변경한다")
    void changeMarkets_whenUpdatesExist_updatesExistingMarkets() {
        JpaMarket btc = createJpaMarket(
                1L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        JpaMarket eth = createJpaMarket(
                2L,
                "KRW-ETH",
                "ETH",
                "이더리움",
                "Ethereum"
        );

        ChangeMarketsCommand command = new ChangeMarketsCommand(
                List.of(),
                List.of(
                        new UpdateMarketCommand(
                                1L,
                                "KRW-BTC",
                                "BTC",
                                "비트코인 변경",
                                "Bitcoin Updated",
                                false
                        ),
                        new UpdateMarketCommand(
                                2L,
                                "KRW-ETH",
                                "ETH",
                                "이더리움 변경",
                                "Ethereum Updated",
                                false
                        )
                ),
                List.of()
        );

        when(marketRepository.findAllById(anyCollection()))
                .thenReturn(List.of(btc, eth));

        sut.changeMarkets(command);

        verify(marketRepository).findAllById(anyCollection());
        verify(marketRepository, never()).saveAll(anyCollection());

        assertThat(btc.getKoreanName()).isEqualTo("비트코인 변경");
        assertThat(btc.getEnglishName()).isEqualTo("Bitcoin Updated");
        assertThat(btc.isEnabled()).isFalse();

        assertThat(eth.getKoreanName()).isEqualTo("이더리움 변경");
        assertThat(eth.getEnglishName()).isEqualTo("Ethereum Updated");
        assertThat(eth.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("changeMarkets는 update 대상 일부가 없으면 예외를 던진다")
    void changeMarkets_whenSomeUpdateTargetsNotFound_throwsException() {
        JpaMarket btc = createJpaMarket(
                1L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        ChangeMarketsCommand command = new ChangeMarketsCommand(
                List.of(),
                List.of(
                        new UpdateMarketCommand(
                                1L,
                                "KRW-BTC",
                                "BTC",
                                "비트코인 변경",
                                "Bitcoin Updated",
                                true
                        ),
                        new UpdateMarketCommand(
                                2L,
                                "KRW-ETH",
                                "ETH",
                                "이더리움 변경",
                                "Ethereum Updated",
                                true
                        )
                ),
                List.of()
        );

        when(marketRepository.findAllById(anyCollection()))
                .thenReturn(List.of(btc));

        assertThatThrownBy(() -> sut.changeMarkets(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Some markets to update were not found.");

        verify(marketRepository).findAllById(anyCollection());
        verify(marketRepository, never()).saveAll(anyCollection());
    }

    @Test
    @DisplayName("changeMarkets는 delete 명령이 있으면 id 목록으로 일괄 삭제한다")
    void changeMarkets_whenDeletesExist_deletesByIds() {
        ChangeMarketsCommand command = new ChangeMarketsCommand(
                List.of(),
                List.of(),
                List.of(
                        new DeleteMarketCommand(1L),
                        new DeleteMarketCommand(2L)
                )
        );

        sut.changeMarkets(command);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);

        verify(marketRepository).deleteAllByIdInBatch(captor.capture());

        assertThat(captor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("changeMarkets는 delete, update, create 순서로 처리한다")
    void changeMarkets_processesDeleteUpdateCreateInOrder() {
        JpaMarket btc = createJpaMarket(
                1L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        ChangeMarketsCommand command = new ChangeMarketsCommand(
                List.of(
                        new CreateMarketCommand(
                                "KRW-ETH",
                                "ETH",
                                "이더리움",
                                "Ethereum",
                                true
                        )
                ),
                List.of(
                        new UpdateMarketCommand(
                                1L,
                                "KRW-BTC",
                                "BTC",
                                "비트코인 변경",
                                "Bitcoin Updated",
                                true
                        )
                ),
                List.of(
                        new DeleteMarketCommand(3L)
                )
        );

        when(marketRepository.findAllById(anyCollection()))
                .thenReturn(List.of(btc));

        sut.changeMarkets(command);

        InOrder inOrder = inOrder(marketRepository);

        inOrder.verify(marketRepository).deleteAllByIdInBatch(List.of(3L));
        inOrder.verify(marketRepository).findAllById(anyCollection());
        inOrder.verify(marketRepository).saveAll(anyCollection());
    }

    private JpaMarket createJpaMarket(
            Long id,
            String marketCode,
            String symbol,
            String koreanName,
            String englishName
    ) {
        JpaMarket market = JpaMarket.create(
                marketCode,
                symbol,
                koreanName,
                englishName,
                true
        );

        ReflectionTestUtils.setField(market, "id", id);

        return market;
    }
}