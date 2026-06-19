package org.example.market.adapter.out.persistence;

import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.DeletePriceAlertSettingCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaPriceAlertSettingAdapterTest {

    @Mock
    private JpaPriceAlertSettingRepository priceAlertSettingRepository;

    @Mock
    private JpaMarketRepository marketRepository;

    @InjectMocks
    private JpaPriceAlertSettingAdapter sut;

    @Test
    @DisplayName("changeSettings는 command가 비어 있으면 아무 작업도 하지 않는다")
    void changeSettings_whenCommandIsEmpty_doesNothing() {
        UUID userPublicId = UUID.randomUUID();

        ChangePriceAlertSettingsCommand command = new ChangePriceAlertSettingsCommand(
                List.of(),
                List.of(),
                List.of()
        );

        sut.changeSettings(userPublicId, command);

        verifyNoInteractions(priceAlertSettingRepository, marketRepository);
    }

    @Test
    @DisplayName("changeSettings는 create 명령이 있으면 enabled market에 대해서만 설정을 생성한다")
    void changeSettings_whenCreatesExist_createsOnlyEnabledMarketSettings() {
        UUID userPublicId = UUID.randomUUID();

        JpaMarket btc = createJpaMarket(
                1L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        ChangePriceAlertSettingsCommand command = new ChangePriceAlertSettingsCommand(
                List.of(
                        new CreatePriceAlertSettingCommand(
                                "KRW-BTC",
                                true,
                                new BigDecimal("0.03")
                        ),
                        new CreatePriceAlertSettingCommand(
                                "KRW-ETH",
                                true,
                                new BigDecimal("0.05")
                        )
                ),
                List.of(),
                List.of()
        );

        when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                .thenReturn(List.of(btc));

        when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                .thenReturn(List.of());

        sut.changeSettings(userPublicId, command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JpaPriceAlertSetting>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(priceAlertSettingRepository).saveAll(captor.capture());

        List<JpaPriceAlertSetting> savedSettings = captor.getValue();

        assertThat(savedSettings).hasSize(1);

        JpaPriceAlertSetting savedSetting = savedSettings.get(0);

        assertThat(savedSetting.getUserPublicId()).isEqualTo(userPublicId);
        assertThat(savedSetting.getMarket().getMarketCode()).isEqualTo("KRW-BTC");
        assertThat(savedSetting.isEnabled()).isTrue();
        assertThat(savedSetting.getTargetChangeRate()).isEqualByComparingTo("0.03");
    }

    @Test
    @DisplayName("changeSettings는 create 명령이어도 기존 설정이 있으면 중복 생성하지 않는다")
    void changeSettings_whenCreateAlreadyExists_doesNotCreateDuplicate() {
        UUID userPublicId = UUID.randomUUID();

        JpaMarket btc = createJpaMarket(
                1L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        JpaPriceAlertSetting existingSetting = createJpaPriceAlertSetting(
                10L,
                userPublicId,
                btc,
                new BigDecimal("0.03")
        );

        ChangePriceAlertSettingsCommand command = new ChangePriceAlertSettingsCommand(
                List.of(
                        new CreatePriceAlertSettingCommand(
                                "KRW-BTC",
                                true,
                                new BigDecimal("0.03")
                        )
                ),
                List.of(),
                List.of()
        );

        when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                .thenReturn(List.of(btc));

        when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                .thenReturn(List.of(existingSetting));

        sut.changeSettings(userPublicId, command);

        verify(priceAlertSettingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("changeSettings는 update 명령이 있으면 enabled market의 기존 설정만 수정한다")
    void changeSettings_whenUpdatesExist_updatesOnlyExistingEnabledMarketSettings() {
        UUID userPublicId = UUID.randomUUID();

        JpaMarket btc = createJpaMarket(
                1L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        JpaPriceAlertSetting existingSetting = createJpaPriceAlertSetting(
                10L,
                userPublicId,
                btc,
                new BigDecimal("0.03")
        );

        ChangePriceAlertSettingsCommand command = new ChangePriceAlertSettingsCommand(
                List.of(),
                List.of(
                        new UpdatePriceAlertSettingCommand(
                                "KRW-BTC",
                                false,
                                new BigDecimal("0.05")
                        ),
                        new UpdatePriceAlertSettingCommand(
                                "KRW-ETH",
                                true,
                                new BigDecimal("0.07")
                        )
                ),
                List.of()
        );

        when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                .thenReturn(List.of(btc));

        when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                .thenReturn(List.of(existingSetting));

        sut.changeSettings(userPublicId, command);

        assertThat(existingSetting.isEnabled()).isFalse();
        assertThat(existingSetting.getTargetChangeRate()).isEqualByComparingTo("0.05");

        verify(priceAlertSettingRepository, never()).saveAll(any());
        verify(priceAlertSettingRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    @DisplayName("changeSettings는 delete 명령이 있으면 기존 설정만 삭제한다")
    void changeSettings_whenDeletesExist_deletesOnlyExistingSettings() {
        UUID userPublicId = UUID.randomUUID();

        JpaMarket btc = createJpaMarket(
                1L,
                "KRW-BTC",
                "BTC",
                "비트코인",
                "Bitcoin"
        );

        JpaPriceAlertSetting existingSetting = createJpaPriceAlertSetting(
                10L,
                userPublicId,
                btc,
                new BigDecimal("0.03")
        );

        ChangePriceAlertSettingsCommand command = new ChangePriceAlertSettingsCommand(
                List.of(),
                List.of(),
                List.of(
                        new DeletePriceAlertSettingCommand("KRW-BTC"),
                        new DeletePriceAlertSettingCommand("KRW-ETH")
                )
        );

        when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                .thenReturn(List.of(existingSetting));

        sut.changeSettings(userPublicId, command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);

        verify(priceAlertSettingRepository).deleteAllByIdInBatch(captor.capture());

        assertThat(captor.getValue()).containsExactly(10L);

        verifyNoInteractions(marketRepository);
    }

    @Test
    @DisplayName("changeSettings는 delete, update, create 순서로 처리한다")
    void changeSettings_processesDeleteUpdateCreateInOrder() {
        UUID userPublicId = UUID.randomUUID();

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

        JpaPriceAlertSetting existingBtcSetting = createJpaPriceAlertSetting(
                10L,
                userPublicId,
                btc,
                new BigDecimal("0.03")
        );

        ChangePriceAlertSettingsCommand command = new ChangePriceAlertSettingsCommand(
                List.of(
                        new CreatePriceAlertSettingCommand(
                                "KRW-ETH",
                                true,
                                new BigDecimal("0.05")
                        )
                ),
                List.of(
                        new UpdatePriceAlertSettingCommand(
                                "KRW-BTC",
                                false,
                                new BigDecimal("0.07")
                        )
                ),
                List.of(
                        new DeletePriceAlertSettingCommand("KRW-BTC")
                )
        );

        when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                .thenReturn(List.of(btc, eth));

        when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                .thenReturn(List.of(existingBtcSetting));

        sut.changeSettings(userPublicId, command);

        InOrder inOrder = inOrder(priceAlertSettingRepository);

        inOrder.verify(priceAlertSettingRepository).findAllByUserPublicIdAndMarketCodeIn(any(), anySet());
        inOrder.verify(priceAlertSettingRepository).deleteAllByIdInBatch(List.of(10L));
        inOrder.verify(priceAlertSettingRepository).saveAll(any());

        assertThat(existingBtcSetting.isEnabled()).isFalse();
        assertThat(existingBtcSetting.getTargetChangeRate()).isEqualByComparingTo("0.07");
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

    private JpaPriceAlertSetting createJpaPriceAlertSetting(
            Long id,
            UUID userPublicId,
            JpaMarket market,
            BigDecimal targetChangeRate
    ) {
        JpaPriceAlertSetting setting = JpaPriceAlertSetting.create(
                userPublicId,
                market,
                true,
                targetChangeRate
        );

        ReflectionTestUtils.setField(setting, "id", id);

        return setting;
    }
}