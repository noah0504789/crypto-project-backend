package org.example.market.adapter.out.persistence;

import org.example.market.application.service.command.ChangeMarketsCommand.CreateMarketCommand;
import org.example.market.application.service.command.ChangeMarketsCommand.UpdateMarketCommand;
import org.example.market.domain.model.Market;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JpaMarketAdapterTest {

    @Mock
    private JpaMarketRepository marketRepository;

    @InjectMocks
    private JpaMarketAdapter sut;

    @Nested
    @DisplayName("findAllEnabledOrderByIdAsc")
    class FindAllEnabledOrderByIdAscTest {

        @Test
        @DisplayName("enabled 마켓을 id 오름차순으로 조회해 도메인으로 변환한다")
        void findAllEnabledOrderByIdAsc_shouldReturnEnabledMarkets() {
            // given
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

            given(marketRepository.findAllByEnabledTrueOrderByIdAsc())
                    .willReturn(List.of(btc, eth));

            // when
            List<Market> result = sut.findAllEnabledOrderByIdAsc();

            // then
            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(Market::getMarketCode)
                    .containsExactly("KRW-BTC", "KRW-ETH");

            verify(marketRepository).findAllByEnabledTrueOrderByIdAsc();
        }
    }

    @Nested
    @DisplayName("findAllEnabledByIds")
    class FindAllEnabledByIdsTest {

        @Test
        @DisplayName("ids가 null이면 빈 목록을 반환하고 repository를 호출하지 않는다")
        void findAllEnabledByIds_whenIdsIsNull_shouldReturnEmptyList() {
            // when
            List<Market> result = sut.findAllEnabledByIds(null);

            // then
            assertThat(result).isEmpty();

            verifyNoInteractions(marketRepository);
        }

        @Test
        @DisplayName("ids가 비어 있으면 빈 목록을 반환하고 repository를 호출하지 않는다")
        void findAllEnabledByIds_whenIdsIsEmpty_shouldReturnEmptyList() {
            // when
            List<Market> result = sut.findAllEnabledByIds(Set.of());

            // then
            assertThat(result).isEmpty();

            verifyNoInteractions(marketRepository);
        }

        @Test
        @DisplayName("ids에 해당하는 enabled 마켓을 조회해 도메인으로 변환한다")
        void findAllEnabledByIds_shouldReturnEnabledMarketsByIds() {
            // given
            Set<Long> ids = Set.of(1L, 2L);

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

            given(marketRepository.findAllByIdInAndEnabledTrue(ids))
                    .willReturn(List.of(btc, eth));

            // when
            List<Market> result = sut.findAllEnabledByIds(ids);

            // then
            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(Market::getMarketCode)
                    .containsExactly("KRW-BTC", "KRW-ETH");

            verify(marketRepository).findAllByIdInAndEnabledTrue(ids);
        }
    }

    @Nested
    @DisplayName("createMarkets")
    class CreateMarketsTest {

        @Test
        @DisplayName("create 명령이 비어 있으면 빈 목록으로 saveAll을 호출한다")
        void createMarkets_whenCommandsIsEmpty_shouldSaveEmptyList() {
            // when
            sut.createMarkets(List.of());

            // then
            verify(marketRepository).saveAll(List.of());
        }

        @Test
        @DisplayName("create 명령이 있으면 JpaMarket을 생성해 저장한다")
        void createMarkets_shouldSaveCreatedMarkets() {
            // given
            List<CreateMarketCommand> commands = List.of(
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
            );

            // when
            sut.createMarkets(commands);

            // then
            @SuppressWarnings("unchecked")
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
    }

    @Nested
    @DisplayName("updateMarkets")
    class UpdateMarketsTest {

        @Test
        @DisplayName("update 명령이 비어 있으면 findAllById를 빈 id 목록으로 호출한다")
        void updateMarkets_whenCommandsIsEmpty_shouldFindAllByEmptyIds() {
            // given
            given(marketRepository.findAllById(Set.of()))
                    .willReturn(List.of());

            // when
            sut.updateMarkets(List.of());

            // then
            verify(marketRepository).findAllById(Set.of());
            verify(marketRepository, never()).saveAll(anyCollection());
        }

        @Test
        @DisplayName("update 명령이 있으면 기존 JpaMarket을 조회해 값을 변경한다")
        void updateMarkets_shouldUpdateExistingMarkets() {
            // given
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

            List<UpdateMarketCommand> commands = List.of(
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
            );

            given(marketRepository.findAllById(anyCollection()))
                    .willReturn(List.of(btc, eth));

            // when
            sut.updateMarkets(commands);

            // then
            verify(marketRepository).findAllById(anyCollection());
            verify(marketRepository, never()).saveAll(anyCollection());

            assertThat(btc.getMarketCode()).isEqualTo("KRW-BTC");
            assertThat(btc.getSymbol()).isEqualTo("BTC");
            assertThat(btc.getKoreanName()).isEqualTo("비트코인 변경");
            assertThat(btc.getEnglishName()).isEqualTo("Bitcoin Updated");
            assertThat(btc.isEnabled()).isFalse();

            assertThat(eth.getMarketCode()).isEqualTo("KRW-ETH");
            assertThat(eth.getSymbol()).isEqualTo("ETH");
            assertThat(eth.getKoreanName()).isEqualTo("이더리움 변경");
            assertThat(eth.getEnglishName()).isEqualTo("Ethereum Updated");
            assertThat(eth.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("update 대상 일부가 없으면 예외를 던진다")
        void updateMarkets_whenSomeUpdateTargetsNotFound_shouldThrowException() {
            // given
            JpaMarket btc = createJpaMarket(
                    1L,
                    "KRW-BTC",
                    "BTC",
                    "비트코인",
                    "Bitcoin"
            );

            List<UpdateMarketCommand> commands = List.of(
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
            );

            given(marketRepository.findAllById(anyCollection()))
                    .willReturn(List.of(btc));

            // when & then
            assertThatThrownBy(() -> sut.updateMarkets(commands))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Some markets to update were not found.");

            verify(marketRepository).findAllById(anyCollection());
            verify(marketRepository, never()).saveAll(anyCollection());
            verify(marketRepository, never()).deleteAllByIdInBatch(anyCollection());
        }
    }

    @Nested
    @DisplayName("deleteMarketsByIds")
    class DeleteMarketsByIdsTest {

        @Test
        @DisplayName("marketIds가 비어 있어도 repository에 삭제 요청을 위임한다")
        void deleteMarketsByIds_whenMarketIdsIsEmpty_shouldDelegateToRepository() {
            // when
            sut.deleteMarketsByIds(List.of());

            // then
            verify(marketRepository).deleteAllByIdInBatch(List.of());
        }

        @Test
        @DisplayName("id 목록으로 일괄 삭제한다")
        void deleteMarketsByIds_shouldDeleteByIds() {
            // given
            List<Long> marketIds = List.of(1L, 2L);

            // when
            sut.deleteMarketsByIds(marketIds);

            // then
            verify(marketRepository).deleteAllByIdInBatch(marketIds);
        }
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