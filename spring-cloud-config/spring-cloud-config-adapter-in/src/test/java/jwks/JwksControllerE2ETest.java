package jwks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.configserver.jwks.adapter.in.JwksController;
import org.example.configserver.jwks.JwksService;
import org.example.configserver.sign.JwtSigningService;
import org.example.configserver.sign.dto.SignRequest;
import org.example.configserver.sign.dto.SignResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class JwksControllerE2ETest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private JwksService jwksService;

    @Mock
    private JwtSigningService jwtSigningService;

    @BeforeEach
    void setUp() {
        JwksController controller = new JwksController(
                jwksService,
                jwtSigningService
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addPlaceholderValue("api-path.jwks", "/.well-known/jwks.json")
                .addPlaceholderValue("api-path.sign", "/sign")
                .build();
    }

    @Test
    @DisplayName("GET /.well-known/jwks.json 요청 시 JWKS를 반환한다")
    void getJwks() throws Exception {
        // given
        String keyName = "jwt-key";

        Map<String, Object> jwks = Map.of(
                "keys", List.of(
                        Map.of(
                                "kid", "jwt-key:1",
                                "alg", "RS256",
                                "use", "sig",
                                "kty", "RSA",
                                "n", "modulus",
                                "e", "AQAB"
                        )
                )
        );

        when(jwksService.getJwks(keyName)).thenReturn(jwks);

        // when & then
        mockMvc.perform(get("/.well-known/jwks.json")
                        .param("keyName", keyName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("jwt-key:1"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].n").value("modulus"))
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"));

        verify(jwksService).getJwks(keyName);
    }

    @Test
    @DisplayName("POST /sign 요청 시 JWT signature 응답을 반환한다")
    void sign() throws Exception {
        // given
        SignRequest request = new SignRequest(
                "jwt-key",
                1,
                "header-b64u",
                "payload-b64u"
        );

        SignResponse response = new SignResponse(
                "jwt-key:1",
                "RS256",
                "signature-b64u"
        );

        when(jwtSigningService.sign(request)).thenReturn(response);

        // when & then
        mockMvc.perform(post("/sign")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kid").value("jwt-key:1"))
                .andExpect(jsonPath("$.alg").value("RS256"))
                .andExpect(jsonPath("$.sigB64u").value("signature-b64u"));

        verify(jwtSigningService).sign(request);
    }

    @Test
    @DisplayName("POST /sign 요청 본문이 SignRequest로 변환되어 서비스에 전달된다")
    void passSignRequestToService() throws Exception {
        // given
        SignRequest request = new SignRequest(
                "oauth-key",
                7,
                "header",
                "payload"
        );

        SignResponse response = new SignResponse(
                "oauth-key:7",
                "RS256",
                "sig"
        );

        when(jwtSigningService.sign(org.mockito.ArgumentMatchers.any(SignRequest.class)))
                .thenReturn(response);

        ArgumentCaptor<SignRequest> captor =
                ArgumentCaptor.forClass(SignRequest.class);

        // when
        mockMvc.perform(post("/sign")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        verify(jwtSigningService).sign(captor.capture());

        SignRequest captured = captor.getValue();

        assertThat(captured.keyName()).isEqualTo("oauth-key");
        assertThat(captured.keyVersion()).isEqualTo(7);
        assertThat(captured.headerB64u()).isEqualTo("header");
        assertThat(captured.payloadB64u()).isEqualTo("payload");
    }
}
