package org.example.market.application.service;

import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.port.out.PriceAlertSettingPersistencePort;
import org.example.market.application.service.result.MyPriceAlertSettingResult;
import org.example.market.domain.model.Market;
import org.example.market.domain.model.PriceAlertSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertSettingQueryServiceTest {

    @Mock
    private PriceAlertSettingPersistencePort priceAlertSettingPersistencePort;

    @Mock
    private MarketPersistencePort marketPersistencePort;

    @InjectMocks
    private PriceAlertSettingQueryService sut;

    @Test
    @DisplayName("getMySettings(): 저장된 가격 알림 설정이 없으면 빈 리스트를 반환한다")
    void getMySettings_whenSettingsAreEmpty_returnsEmptyList() {
        UUID userPublicId = UUID.randomUUID();

        when(priceAlertSettingPersistencePort.findAllByUserPublicId(userPublicId))
                .thenReturn(List.of());

        List<MyPriceAlertSettingResult> results = sut.getMySettings(userPublicId);

        assertThat(results).isEmpty();

        verify(priceAlertSettingPersistencePort).findAllByUserPublicId(userPublicId);
        verify(marketPersistencePort, never()).findAllEnabledByIds(Set.of());
    }

    @Test
    @DisplayName("getMySettings(): enabled market에 해당하는 설정만 반환한다")
    void getMySettings_returnsOnlySettingsOfEnabledMarkets() {
        UUID userPublicId = UUID.randomUUID();

        PriceAlertSetting btcSetting = createPriceAlertSetting(
                1L,
                userPublicId,
                10L,
                true,
                new BigDecimal("0.03")
        );

        PriceAlertSetting ethSetting = createPriceAlertSetting(
                2L,
                userPublicId,
                20L,
                false,
                new BigDecimal("0.05")
        );

        PriceAlertSetting xrpSetting = createPriceAlertSetting(
                3L,
                userPublicId,
                30L,
                true,
                new BigDecimal("0.07")
        );

        Market btcMarket = createMarket(
                10L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        Market xrpMarket = createMarket(
                30L,
                "KRW-XRP",
                "XRP",
                "엑스알피",
                "XRP"
        );

        when(priceAlertSettingPersistencePort.findAllByUserPublicId(userPublicId))
                .thenReturn(List.of(btcSetting, ethSetting, xrpSetting));

        when(marketPersistencePort.findAllEnabledByIds(Set.of(10L, 20L, 30L)))
                .thenReturn(List.of(btcMarket, xrpMarket));

        List<MyPriceAlertSettingResult> results = sut.getMySettings(userPublicId);

        assertThat(results).hasSize(2);

        assertThat(results)
                .extracting(MyPriceAlertSettingResult::code)
                .containsExactly("KRW-BTC", "KRW-XRP");

        assertThat(results)
                .extracting(MyPriceAlertSettingResult::enabled)
                .containsExactly(true, true);

        assertThat(results)
                .extracting(MyPriceAlertSettingResult::targetChangeRate)
                .containsExactly(
                        new BigDecimal("0.03"),
                        new BigDecimal("0.07")
                );

        verify(priceAlertSettingPersistencePort).findAllByUserPublicId(userPublicId);
        verify(marketPersistencePort).findAllEnabledByIds(Set.of(10L, 20L, 30L));
    }

    @Test
    @DisplayName("getMySettings(): PriceAlertSetting의 enabled 값을 그대로 응답한다")
    void getMySettings_preservesSettingEnabledValue() {
        UUID userPublicId = UUID.randomUUID();

        PriceAlertSetting setting = createPriceAlertSetting(
                1L,
                userPublicId,
                10L,
                false,
                new BigDecimal("0.05")
        );

        Market market = createMarket(
                10L,
                "KRW-ETH",
                "ETH",
                "이더리움",
                "Ethereum"
        );

        when(priceAlertSettingPersistencePort.findAllByUserPublicId(userPublicId))
                .thenReturn(List.of(setting));

        when(marketPersistencePort.findAllEnabledByIds(Set.of(10L)))
                .thenReturn(List.of(market));

        List<MyPriceAlertSettingResult> results = sut.getMySettings(userPublicId);

        assertThat(results).hasSize(1);

        MyPriceAlertSettingResult result = results.get(0);

        assertThat(result.code()).isEqualTo("KRW-ETH");
        assertThat(result.enabled()).isFalse();
        assertThat(result.targetChangeRate()).isEqualByComparingTo("0.05");
    }

    private PriceAlertSetting createPriceAlertSetting(
            Long id,
            UUID userPublicId,
            Long marketId,
            boolean enabled,
            BigDecimal targetChangeRate
    ) {
        return PriceAlertSetting.rehydrate(
                id,
                userPublicId,
                marketId,
                enabled,
                targetChangeRate,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private Market createMarket(
            Long id,
            String marketCode,
            String symbol,
            String koreanName,
            String englishName
    ) {
        return Market.rehydrate(
                id,
                marketCode,
                symbol,
                koreanName,
                englishName,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}