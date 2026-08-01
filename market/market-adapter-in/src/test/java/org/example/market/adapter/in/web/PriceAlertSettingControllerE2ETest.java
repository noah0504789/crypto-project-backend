package org.example.market.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.enums.HttpHeaderKey;
import org.example.common.exception.GlobalExceptionHandler;
import org.example.common.test.config.TestBootApplication;
import org.example.market.adapter.in.web.dto.PriceAlertSettingChangeRequest;
import org.example.market.application.port.in.PriceAlertSettingCommandUseCase;
import org.example.market.application.port.in.PriceAlertSettingQueryUseCase;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;
import org.example.market.application.service.result.MyPriceAlertSettingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceAlertSettingController.class)
@ContextConfiguration(classes = {
        TestBootApplication.class,
        PriceAlertSettingController.class,
        GlobalExceptionHandler.class
})
class PriceAlertSettingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PriceAlertSettingQueryUseCase priceAlertSettingQueryUseCase;

    @MockitoBean
    private PriceAlertSettingCommandUseCase priceAlertSettingCommandUseCase;

    @Nested
    @DisplayName("getMySettings")
    class GetMySettingsTest {

        @Test
        @DisplayName("내 가격 알림 설정 목록을 조회한다")
        void getMySettings_shouldReturnMyPriceAlertSettings() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            List<MyPriceAlertSettingResult> results = List.of(
                    new MyPriceAlertSettingResult(
                            "KRW-BTC",
                            true,
                            new BigDecimal("0.03")
                    ),
                    new MyPriceAlertSettingResult(
                            "KRW-ETH",
                            false,
                            new BigDecimal("0.05")
                    )
            );

            given(priceAlertSettingQueryUseCase.getMySettings(userPublicId))
                    .willReturn(results);

            // when & then
            mockMvc.perform(
                            get("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.settings").isArray())
                    .andExpect(jsonPath("$.settings.length()").value(2))
                    .andExpect(jsonPath("$.settings[0].code").value("KRW-BTC"))
                    .andExpect(jsonPath("$.settings[0].enabled").value(true))
                    .andExpect(jsonPath("$.settings[0].targetChangeRate").value(0.03))
                    .andExpect(jsonPath("$.settings[1].code").value("KRW-ETH"))
                    .andExpect(jsonPath("$.settings[1].enabled").value(false))
                    .andExpect(jsonPath("$.settings[1].targetChangeRate").value(0.05));

            then(priceAlertSettingQueryUseCase)
                    .should()
                    .getMySettings(userPublicId);

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("내 가격 알림 설정이 없으면 빈 목록을 응답한다")
        void getMySettings_shouldReturnEmptySettings_whenNoSettingsExist() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            given(priceAlertSettingQueryUseCase.getMySettings(userPublicId))
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(
                            get("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.settings").isArray())
                    .andExpect(jsonPath("$.settings.length()").value(0));

            then(priceAlertSettingQueryUseCase)
                    .should()
                    .getMySettings(userPublicId);

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("changeMySettings - 성공")
    class ChangeMySettingsSuccessTest {

        @Test
        @DisplayName("updates만 있으면 creates와 deletes가 비어 있어도 정상 처리한다")
        void changeMySettings_shouldWork_whenOnlyUpdatesExist() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(),
                    List.of(
                            new PriceAlertSettingChangeRequest.UpdatePriceAlertSettingRequest(
                                    "KRW-BTC",
                                    true,
                                    new BigDecimal("0.03")
                            )
                    ),
                    List.of()
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNoContent());

            then(priceAlertSettingCommandUseCase)
                    .should()
                    .changeMySettings(
                            eq(userPublicId),
                            argThat(command ->
                                    command.creates().isEmpty()
                                            && command.updates().size() == 1
                                            && command.deletes().isEmpty()
                                            && command.updates().get(0).code().equals("KRW-BTC")
                                            && command.updates().get(0).enabled()
                                            && command.updates().get(0).targetChangeRate()
                                            .compareTo(new BigDecimal("0.03")) == 0
                            )
                    );

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("creates, updates, deletes가 함께 있으면 정상 처리한다")
        void changeMySettings_shouldWork_whenCreatesUpdatesDeletesExistTogether() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(
                            new PriceAlertSettingChangeRequest.CreatePriceAlertSettingRequest(
                                    "KRW-BTC",
                                    true,
                                    new BigDecimal("0.03")
                            )
                    ),
                    List.of(
                            new PriceAlertSettingChangeRequest.UpdatePriceAlertSettingRequest(
                                    "KRW-ETH",
                                    false,
                                    new BigDecimal("0.05")
                            )
                    ),
                    List.of(
                            new PriceAlertSettingChangeRequest.DeletePriceAlertSettingRequest(
                                    "KRW-XRP"
                            )
                    )
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNoContent());

            then(priceAlertSettingCommandUseCase)
                    .should()
                    .changeMySettings(
                            eq(userPublicId),
                            argThat(command ->
                                    command.creates().size() == 1
                                            && command.updates().size() == 1
                                            && command.deletes().size() == 1

                                            && command.creates().get(0).code().equals("KRW-BTC")
                                            && command.creates().get(0).enabled()
                                            && command.creates().get(0).targetChangeRate()
                                            .compareTo(new BigDecimal("0.03")) == 0

                                            && command.updates().get(0).code().equals("KRW-ETH")
                                            && !command.updates().get(0).enabled()
                                            && command.updates().get(0).targetChangeRate()
                                            .compareTo(new BigDecimal("0.05")) == 0

                                            && command.deletes().get(0).code().equals("KRW-XRP")
                            )
                    );

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("updates 키만 있어도 정상 처리한다")
        void changeMySettings_shouldWork_whenOnlyUpdatesKeyExists() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            String requestBody = """
                    {
                      "updates": [
                        {
                          "code": "KRW-BTC",
                          "enabled": true,
                          "targetChangeRate": 0.03
                        }
                      ]
                    }
                    """;

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isNoContent());

            then(priceAlertSettingCommandUseCase)
                    .should()
                    .changeMySettings(
                            eq(userPublicId),
                            argThat(command ->
                                    command.creates().isEmpty()
                                            && command.updates().size() == 1
                                            && command.deletes().isEmpty()
                                            && command.updates().get(0).code().equals("KRW-BTC")
                                            && command.updates().get(0).enabled()
                                            && command.updates().get(0).targetChangeRate()
                                            .compareTo(new BigDecimal("0.03")) == 0
                            )
                    );

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("creates, updates, deletes 키가 모두 없어도 빈 command로 정상 처리한다")
        void changeMySettings_shouldWork_whenAllKeysAreMissing() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")
                    )
                    .andExpect(status().isNoContent());

            then(priceAlertSettingCommandUseCase)
                    .should()
                    .changeMySettings(
                            eq(userPublicId),
                            argThat(ChangePriceAlertSettingsCommand::isEmpty)
                    );

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("creates, updates, deletes가 모두 null이어도 빈 command로 정상 처리한다")
        void changeMySettings_shouldWork_whenListsAreNull() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request =
                    new PriceAlertSettingChangeRequest(null, null, null);

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNoContent());

            then(priceAlertSettingCommandUseCase)
                    .should()
                    .changeMySettings(
                            eq(userPublicId),
                            argThat(ChangePriceAlertSettingsCommand::isEmpty)
                    );

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("changeMySettings - Validation 실패")
    class ChangeMySettingsValidationFailTest {

        @Test
        @DisplayName("create code가 공백이면 ValidationResult 형식으로 응답한다")
        void changeMySettings_shouldReturnValidationResult_whenCreateCodeIsBlank() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(
                            new PriceAlertSettingChangeRequest.CreatePriceAlertSettingRequest(
                                    "",
                                    true,
                                    new BigDecimal("0.03")
                            )
                    ),
                    List.of(),
                    List.of()
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("code"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlank"))
                    .andExpect(jsonPath("$.errors[0].message").value("마켓 코드는 필수입니다."));

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("update targetChangeRate가 null이면 ValidationResult 형식으로 응답한다")
        void changeMySettings_shouldReturnValidationResult_whenUpdateTargetChangeRateIsNull() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(),
                    List.of(
                            new PriceAlertSettingChangeRequest.UpdatePriceAlertSettingRequest(
                                    "KRW-BTC",
                                    true,
                                    null
                            )
                    ),
                    List.of()
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("targetChangeRate"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotNull"))
                    .andExpect(jsonPath("$.errors[0].message").value("목표 변화율은 필수입니다."));

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("update targetChangeRate가 0.01보다 작으면 ValidationResult 형식으로 응답한다")
        void changeMySettings_shouldReturnValidationResult_whenUpdateTargetChangeRateIsTooSmall() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(),
                    List.of(
                            new PriceAlertSettingChangeRequest.UpdatePriceAlertSettingRequest(
                                    "KRW-BTC",
                                    true,
                                    new BigDecimal("0.001")
                            )
                    ),
                    List.of()
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("targetChangeRate"))
                    .andExpect(jsonPath("$.errors[0].code").value("DecimalMin"))
                    .andExpect(jsonPath("$.errors[0].message").value("목표 변화율은 0.01 이상이어야 합니다."));

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("update targetChangeRate가 1.00보다 크면 ValidationResult 형식으로 응답한다")
        void changeMySettings_shouldReturnValidationResult_whenUpdateTargetChangeRateIsTooLarge() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(),
                    List.of(
                            new PriceAlertSettingChangeRequest.UpdatePriceAlertSettingRequest(
                                    "KRW-BTC",
                                    true,
                                    new BigDecimal("1.01")
                            )
                    ),
                    List.of()
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("targetChangeRate"))
                    .andExpect(jsonPath("$.errors[0].code").value("DecimalMax"))
                    .andExpect(jsonPath("$.errors[0].message").value("목표 변화율은 1.00 이하이어야 합니다."));

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("delete code가 공백이면 ValidationResult 형식으로 응답한다")
        void changeMySettings_shouldReturnValidationResult_whenDeleteCodeIsBlank() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(),
                    List.of(),
                    List.of(
                            new PriceAlertSettingChangeRequest.DeletePriceAlertSettingRequest("")
                    )
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("code"))
                    .andExpect(jsonPath("$.errors[0].code").value("NotBlank"))
                    .andExpect(jsonPath("$.errors[0].message").value("마켓 코드는 필수입니다."));

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("creates, updates, deletes에 validation 오류가 함께 있으면 errors 배열로 응답한다")
        void changeMySettings_shouldReturnValidationResult_whenMultipleNestedRequestsAreInvalid() throws Exception {
            // given
            UUID userPublicId = UUID.randomUUID();

            PriceAlertSettingChangeRequest request = new PriceAlertSettingChangeRequest(
                    List.of(
                            new PriceAlertSettingChangeRequest.CreatePriceAlertSettingRequest(
                                    "",
                                    true,
                                    new BigDecimal("0.03")
                            )
                    ),
                    List.of(
                            new PriceAlertSettingChangeRequest.UpdatePriceAlertSettingRequest(
                                    "KRW-ETH",
                                    true,
                                    new BigDecimal("1.01")
                            )
                    ),
                    List.of(
                            new PriceAlertSettingChangeRequest.DeletePriceAlertSettingRequest("")
                    )
            );

            // when & then
            mockMvc.perform(
                            put("/price-alerts/me")
                                    .header(HttpHeaderKey.USER_ID_VALUE, userPublicId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors.length()").value(3))
                    .andExpect(jsonPath("$.errors[*].field").value(
                            containsInAnyOrder(
                                    "code",
                                    "targetChangeRate",
                                    "code"
                            )
                    ))
                    .andExpect(jsonPath("$.errors[*].path").value(
                            containsInAnyOrder(
                                    "creates[0].code",
                                    "updates[0].targetChangeRate",
                                    "deletes[0].code"
                            )
                    ))
                    .andExpect(jsonPath("$.errors[*].code").value(
                            containsInAnyOrder(
                                    "NotBlank",
                                    "DecimalMax",
                                    "NotBlank"
                            )
                    ))
                    .andExpect(jsonPath("$.errors[*].message").value(
                            containsInAnyOrder(
                                    "마켓 코드는 필수입니다.",
                                    "목표 변화율은 1.00 이하이어야 합니다.",
                                    "마켓 코드는 필수입니다."
                            )
                    ));

            then(priceAlertSettingCommandUseCase)
                    .shouldHaveNoInteractions();

            then(priceAlertSettingQueryUseCase)
                    .shouldHaveNoInteractions();
        }
    }
}