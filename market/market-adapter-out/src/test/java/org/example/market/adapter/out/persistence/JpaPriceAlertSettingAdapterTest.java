package org.example.market.adapter.out.persistence;

import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Nested
    @DisplayName("createSettings")
    class CreateSettingsTest {

        @Test
        @DisplayName("create 명령이 비어 있으면 아무 작업도 하지 않는다")
        void createSettings_whenCommandsIsEmpty_doesNothing() {
            // given
            UUID userPublicId = UUID.randomUUID();

            // when
            sut.createSettings(userPublicId, List.of());

            // then
            verifyNoInteractions(priceAlertSettingRepository, marketRepository);
        }

        @Test
        @DisplayName("enabled market에 대해서만 설정을 생성한다")
        void createSettings_createsOnlyEnabledMarketSettings() {
            // given
            UUID userPublicId = UUID.randomUUID();

            JpaMarket btc = createJpaMarket(
                    1L,
                    "KRW-BTC",
                    "BTC",
                    "비트코인",
                    "Bitcoin"
            );

            List<CreatePriceAlertSettingCommand> commands = List.of(
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
            );

            when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                    .thenReturn(List.of(btc));

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of());

            // when
            sut.createSettings(userPublicId, commands);

            // then
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
        @DisplayName("기존 설정이 있으면 중복 생성하지 않는다")
        void createSettings_whenSettingAlreadyExists_doesNotCreateDuplicate() {
            // given
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

            List<CreatePriceAlertSettingCommand> commands = List.of(
                    new CreatePriceAlertSettingCommand(
                            "KRW-BTC",
                            true,
                            new BigDecimal("0.03")
                    )
            );

            when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                    .thenReturn(List.of(btc));

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of(existingSetting));

            // when
            sut.createSettings(userPublicId, commands);

            // then
            verify(priceAlertSettingRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("생성 가능한 설정이 없으면 saveAll을 호출하지 않는다")
        void createSettings_whenNoCreatableSettings_doesNotSaveAll() {
            // given
            UUID userPublicId = UUID.randomUUID();

            List<CreatePriceAlertSettingCommand> commands = List.of(
                    new CreatePriceAlertSettingCommand(
                            "KRW-BTC",
                            true,
                            new BigDecimal("0.03")
                    )
            );

            when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                    .thenReturn(List.of());

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of());

            // when
            sut.createSettings(userPublicId, commands);

            // then
            verify(priceAlertSettingRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("updateSettings")
    class UpdateSettingsTest {

        @Test
        @DisplayName("update 명령이 비어 있으면 아무 작업도 하지 않는다")
        void updateSettings_whenCommandsIsEmpty_doesNothing() {
            // given
            UUID userPublicId = UUID.randomUUID();

            // when
            sut.updateSettings(userPublicId, List.of());

            // then
            verifyNoInteractions(priceAlertSettingRepository, marketRepository);
        }

        @Test
        @DisplayName("enabled market의 기존 설정만 수정한다")
        void updateSettings_updatesOnlyExistingEnabledMarketSettings() {
            // given
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

            List<UpdatePriceAlertSettingCommand> commands = List.of(
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
            );

            when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                    .thenReturn(List.of(btc));

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of(existingSetting));

            // when
            sut.updateSettings(userPublicId, commands);

            // then
            assertThat(existingSetting.isEnabled()).isFalse();
            assertThat(existingSetting.getTargetChangeRate()).isEqualByComparingTo("0.05");

            verify(priceAlertSettingRepository, never()).saveAll(any());
            verify(priceAlertSettingRepository, never()).deleteAllByIdInBatch(any());
        }

        @Test
        @DisplayName("enabled market이 아니면 기존 설정이 있어도 수정하지 않는다")
        void updateSettings_whenMarketIsNotEnabled_doesNotUpdate() {
            // given
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

            List<UpdatePriceAlertSettingCommand> commands = List.of(
                    new UpdatePriceAlertSettingCommand(
                            "KRW-BTC",
                            false,
                            new BigDecimal("0.05")
                    )
            );

            when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                    .thenReturn(List.of());

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of(existingSetting));

            // when
            sut.updateSettings(userPublicId, commands);

            // then
            assertThat(existingSetting.isEnabled()).isTrue();
            assertThat(existingSetting.getTargetChangeRate()).isEqualByComparingTo("0.03");
        }

        @Test
        @DisplayName("기존 설정이 없으면 수정하지 않는다")
        void updateSettings_whenSettingDoesNotExist_doesNotUpdate() {
            // given
            UUID userPublicId = UUID.randomUUID();

            JpaMarket btc = createJpaMarket(
                    1L,
                    "KRW-BTC",
                    "BTC",
                    "비트코인",
                    "Bitcoin"
            );

            List<UpdatePriceAlertSettingCommand> commands = List.of(
                    new UpdatePriceAlertSettingCommand(
                            "KRW-BTC",
                            false,
                            new BigDecimal("0.05")
                    )
            );

            when(marketRepository.findAllByMarketCodeInAndEnabledTrue(anySet()))
                    .thenReturn(List.of(btc));

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of());

            // when
            sut.updateSettings(userPublicId, commands);

            // then
            verify(priceAlertSettingRepository, never()).saveAll(any());
            verify(priceAlertSettingRepository, never()).deleteAllByIdInBatch(any());
        }
    }

    @Nested
    @DisplayName("deleteSettingsByCodes")
    class DeleteSettingsByCodesTest {

        @Test
        @DisplayName("delete code가 비어 있으면 아무 작업도 하지 않는다")
        void deleteSettingsByCodes_whenCodesIsEmpty_doesNothing() {
            // given
            UUID userPublicId = UUID.randomUUID();

            // when
            sut.deleteSettingsByCodes(userPublicId, List.of());

            // then
            verifyNoInteractions(priceAlertSettingRepository, marketRepository);
        }

        @Test
        @DisplayName("기존 설정만 삭제한다")
        void deleteSettingsByCodes_deletesOnlyExistingSettings() {
            // given
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

            List<String> codes = List.of("KRW-BTC", "KRW-ETH");

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of(existingSetting));

            // when
            sut.deleteSettingsByCodes(userPublicId, codes);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);

            verify(priceAlertSettingRepository).deleteAllByIdInBatch(captor.capture());

            assertThat(captor.getValue()).containsExactly(10L);

            verifyNoInteractions(marketRepository);
        }

        @Test
        @DisplayName("삭제할 기존 설정이 없으면 deleteAllByIdInBatch를 호출하지 않는다")
        void deleteSettingsByCodes_whenExistingSettingsIsEmpty_doesNotDelete() {
            // given
            UUID userPublicId = UUID.randomUUID();

            List<String> codes = List.of("KRW-BTC", "KRW-ETH");

            when(priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(any(), anySet()))
                    .thenReturn(List.of());

            // when
            sut.deleteSettingsByCodes(userPublicId, codes);

            // then
            verify(priceAlertSettingRepository, never()).deleteAllByIdInBatch(any());
            verifyNoInteractions(marketRepository);
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