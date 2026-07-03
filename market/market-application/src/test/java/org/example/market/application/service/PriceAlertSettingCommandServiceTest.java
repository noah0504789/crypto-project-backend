package org.example.market.application.service;

import org.example.market.application.port.out.PriceAlertSettingPersistencePort;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertSettingCommandServiceTest {

    @Mock
    private PriceAlertSettingPersistencePort priceAlertSettingPersistencePort;

    @InjectMocks
    private PriceAlertSettingCommandService sut;

    @Nested
    @DisplayName("changeMySettings")
    class ChangeMySettingsTest {

        private final UUID userPublicId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        @Test
        @DisplayName("변경할 설정이 없으면 아무 작업도 하지 않는다")
        void changeMySettings_shouldDoNothingWhenCommandIsEmpty() {
            // given
            ChangePriceAlertSettingsCommand command = mock(ChangePriceAlertSettingsCommand.class);

            given(command.isEmpty()).willReturn(true);

            // when
            sut.changeMySettings(userPublicId, command);

            // then
            verify(priceAlertSettingPersistencePort, never()).deleteSettingsByCodes(any(), any());
            verify(priceAlertSettingPersistencePort, never()).updateSettings(any(), any());
            verify(priceAlertSettingPersistencePort, never()).createSettings(any(), any());
        }

        @Test
        @DisplayName("삭제, 수정, 생성 요청이 있으면 순서대로 처리한다")
        void changeMySettings_shouldApplyDeletesUpdatesCreatesInOrder() {
            // given
            ChangePriceAlertSettingsCommand command = mock(ChangePriceAlertSettingsCommand.class);

            List<String> deleteCodes = List.of("KRW-BTC", "KRW-ETH");
            List<UpdatePriceAlertSettingCommand> updates = List.of(
                    new UpdatePriceAlertSettingCommand(
                            "KRW-XRP",
                            true,
                            BigDecimal.valueOf(5)
                    )
            );
            List<CreatePriceAlertSettingCommand> creates = List.of(
                    new CreatePriceAlertSettingCommand(
                            "KRW-SOL",
                            true,
                            BigDecimal.valueOf(7)
                    )
            );

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(true);
            given(command.hasCreates()).willReturn(true);
            given(command.deleteCodes()).willReturn(deleteCodes);
            given(command.updates()).willReturn(updates);
            given(command.creates()).willReturn(creates);

            // when
            sut.changeMySettings(userPublicId, command);

            // then
            InOrder inOrder = inOrder(priceAlertSettingPersistencePort);

            inOrder.verify(priceAlertSettingPersistencePort)
                    .deleteSettingsByCodes(userPublicId, deleteCodes);
            inOrder.verify(priceAlertSettingPersistencePort)
                    .updateSettings(userPublicId, updates);
            inOrder.verify(priceAlertSettingPersistencePort)
                    .createSettings(userPublicId, creates);
        }

        @Test
        @DisplayName("삭제 요청만 있으면 삭제만 처리한다")
        void changeMySettings_shouldDeleteOnly() {
            // given
            ChangePriceAlertSettingsCommand command = mock(ChangePriceAlertSettingsCommand.class);

            List<String> deleteCodes = List.of("KRW-BTC", "KRW-ETH");

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(false);
            given(command.deleteCodes()).willReturn(deleteCodes);

            // when
            sut.changeMySettings(userPublicId, command);

            // then
            verify(priceAlertSettingPersistencePort)
                    .deleteSettingsByCodes(userPublicId, deleteCodes);
            verify(priceAlertSettingPersistencePort, never()).updateSettings(any(), any());
            verify(priceAlertSettingPersistencePort, never()).createSettings(any(), any());
        }

        @Test
        @DisplayName("수정 요청만 있으면 수정만 처리한다")
        void changeMySettings_shouldUpdateOnly() {
            // given
            ChangePriceAlertSettingsCommand command = mock(ChangePriceAlertSettingsCommand.class);

            List<UpdatePriceAlertSettingCommand> updates = List.of(
                    new UpdatePriceAlertSettingCommand(
                            "KRW-BTC",
                            true,
                            BigDecimal.valueOf(3)
                    )
            );

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(false);
            given(command.hasUpdates()).willReturn(true);
            given(command.hasCreates()).willReturn(false);
            given(command.updates()).willReturn(updates);

            // when
            sut.changeMySettings(userPublicId, command);

            // then
            verify(priceAlertSettingPersistencePort, never()).deleteSettingsByCodes(any(), any());
            verify(priceAlertSettingPersistencePort)
                    .updateSettings(userPublicId, updates);
            verify(priceAlertSettingPersistencePort, never()).createSettings(any(), any());
        }

        @Test
        @DisplayName("생성 요청만 있으면 생성만 처리한다")
        void changeMySettings_shouldCreateOnly() {
            // given
            ChangePriceAlertSettingsCommand command = mock(ChangePriceAlertSettingsCommand.class);

            List<CreatePriceAlertSettingCommand> creates = List.of(
                    new CreatePriceAlertSettingCommand(
                            "KRW-BTC",
                            true,
                            BigDecimal.valueOf(5)
                    )
            );

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(false);
            given(command.hasUpdates()).willReturn(false);
            given(command.hasCreates()).willReturn(true);
            given(command.creates()).willReturn(creates);

            // when
            sut.changeMySettings(userPublicId, command);

            // then
            verify(priceAlertSettingPersistencePort, never()).deleteSettingsByCodes(any(), any());
            verify(priceAlertSettingPersistencePort, never()).updateSettings(any(), any());
            verify(priceAlertSettingPersistencePort)
                    .createSettings(userPublicId, creates);
        }

        @Test
        @DisplayName("삭제 처리 중 예외가 발생하면 이후 작업을 수행하지 않고 예외를 전파한다")
        void changeMySettings_shouldStopWhenDeleteFails() {
            // given
            ChangePriceAlertSettingsCommand command = mock(ChangePriceAlertSettingsCommand.class);

            List<String> deleteCodes = List.of("KRW-BTC");
            RuntimeException exception = new RuntimeException("delete failed");

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.deleteCodes()).willReturn(deleteCodes);

            doThrow(exception)
                    .when(priceAlertSettingPersistencePort)
                    .deleteSettingsByCodes(userPublicId, deleteCodes);

            // when & then
            assertThatThrownBy(() -> sut.changeMySettings(userPublicId, command))
                    .isSameAs(exception);

            verify(priceAlertSettingPersistencePort)
                    .deleteSettingsByCodes(userPublicId, deleteCodes);
            verify(priceAlertSettingPersistencePort, never()).updateSettings(any(), any());
            verify(priceAlertSettingPersistencePort, never()).createSettings(any(), any());
        }

        @Test
        @DisplayName("수정 처리 중 예외가 발생하면 생성 작업을 수행하지 않고 예외를 전파한다")
        void changeMySettings_shouldStopWhenUpdateFails() {
            // given
            ChangePriceAlertSettingsCommand command = mock(ChangePriceAlertSettingsCommand.class);

            List<String> deleteCodes = List.of("KRW-BTC");
            List<UpdatePriceAlertSettingCommand> updates = List.of(
                    new UpdatePriceAlertSettingCommand(
                            "KRW-ETH",
                            true,
                            BigDecimal.valueOf(3)
                    )
            );
            RuntimeException exception = new RuntimeException("update failed");

            given(command.isEmpty()).willReturn(false);
            given(command.hasDeletes()).willReturn(true);
            given(command.hasUpdates()).willReturn(true);
            given(command.deleteCodes()).willReturn(deleteCodes);
            given(command.updates()).willReturn(updates);

            doThrow(exception)
                    .when(priceAlertSettingPersistencePort)
                    .updateSettings(userPublicId, updates);

            // when & then
            assertThatThrownBy(() -> sut.changeMySettings(userPublicId, command))
                    .isSameAs(exception);

            InOrder inOrder = inOrder(priceAlertSettingPersistencePort);

            inOrder.verify(priceAlertSettingPersistencePort)
                    .deleteSettingsByCodes(userPublicId, deleteCodes);
            inOrder.verify(priceAlertSettingPersistencePort)
                    .updateSettings(userPublicId, updates);

            verify(priceAlertSettingPersistencePort, never()).createSettings(any(), any());
        }
    }
}