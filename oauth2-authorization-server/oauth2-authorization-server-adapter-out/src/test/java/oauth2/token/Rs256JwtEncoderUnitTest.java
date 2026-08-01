package oauth2.token;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.TestPropertiesConfig;
import config.TestObjectMapperConfig;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.authorizationserver.token.adapter.out.vault.Rs256JwtEncoder;
import org.example.oauth2.authorizationserver.token.adapter.out.vault.dto.SignRequest;
import org.example.oauth2.authorizationserver.token.adapter.out.vault.dto.SignResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = {
        TestObjectMapperConfig.class,
        TestPropertiesConfig.class
})
class Rs256JwtEncoderTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Mock
    private RestTemplate jwtRestTemplate;

    private Rs256JwtEncoder sut;

    private final Instant issuedAt = Instant.parse("2026-05-14T04:00:00Z");
    private final Instant expiresAt = Instant.parse("2026-05-14T05:00:00Z");
    private final Instant notBefore = Instant.parse("2026-05-14T04:00:00Z");

    @BeforeEach
    void setUp() {
        sut = new Rs256JwtEncoder(
                jwtProperties,
                objectMapper,
                jwtRestTemplate
        );
    }

    @Test
    @DisplayName("JWT header와 claims를 Base64Url 인코딩한 뒤 외부 signing 서버 응답으로 JWT를 생성한다")
    void encode_shouldCreateSignedJwt() throws Exception {
        // given
        given(jwtRestTemplate.postForObject(
                eq(jwtProperties.signUri()),
                any(SignRequest.class),
                eq(SignResponse.class)
        )).willReturn(new SignResponse("test-key:1", "RS256", "signature-b64u"));

        JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256)
                .build();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .subject("user@test.com")
                .issuer("http://localhost:9000")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("nbf", notBefore)
                .claim("email", "user@test.com")
                .build();

        JwtEncoderParameters parameters =
                JwtEncoderParameters.from(jwsHeader, claimsSet);

        ArgumentCaptor<SignRequest> signRequestCaptor = ArgumentCaptor.forClass(SignRequest.class);

        // when
        Jwt result = sut.encode(parameters);

        // then
        then(jwtRestTemplate).should().postForObject(
                eq(jwtProperties.signUri()),
                signRequestCaptor.capture(),
                eq(SignResponse.class)
        );

        SignRequest signRequest = signRequestCaptor.getValue();

        assertThat(signRequest.keyName()).isEqualTo(jwtProperties.keyName());
        assertThat(signRequest.keyVersion()).isEqualTo(jwtProperties.keyVersion());

        Map<String, Object> decodedHeader =
                decodeBase64UrlJson(signRequest.headerB64u());

        Map<String, Object> decodedPayload =
                decodeBase64UrlJson(signRequest.payloadB64u());

        assertThat(decodedHeader)
                .containsEntry("alg", "RS256")
                .containsEntry("typ", "JWT")
                .containsEntry("kid", "test-key:1");

        assertThat(decodedPayload)
                .containsEntry("sub", "user@test.com")
                .containsEntry("iss", "http://localhost:9000")
                .containsEntry("email", "user@test.com");

        assertThat(((Number) decodedPayload.get("iat")).longValue()).isEqualTo(issuedAt.getEpochSecond());
        assertThat(((Number) decodedPayload.get("exp")).longValue()).isEqualTo(expiresAt.getEpochSecond());
        assertThat(((Number) decodedPayload.get("nbf")).longValue()).isEqualTo(notBefore.getEpochSecond());

        String expectedToken =
                signRequest.headerB64u()
                        + "."
                        + signRequest.payloadB64u()
                        + "."
                        + "signature-b64u";

        assertThat(result.getTokenValue()).isEqualTo(expectedToken);
        assertThat(result.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);

        assertThat(result.getHeaders())
                .containsEntry("kid", "test-key:1")
                .containsEntry("alg", "RS256")
                .containsEntry("typ", "JWT");
    }

    @Test
    @DisplayName("nbf claim이 없어도 JWT를 정상 생성한다")
    void encode_shouldNotFail_whenNbfMissing() throws Exception {
        // given
        given(jwtRestTemplate.postForObject(
                eq(jwtProperties.signUri()),
                any(SignRequest.class),
                eq(SignResponse.class)
        )).willReturn(new SignResponse("test-key:1", "RS256", "signature-b64u"));

        JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256)
                .build();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .subject("user@test.com")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwtEncoderParameters parameters =
                JwtEncoderParameters.from(jwsHeader, claimsSet);

        ArgumentCaptor<SignRequest> signRequestCaptor =
                ArgumentCaptor.forClass(SignRequest.class);

        // when
        Jwt result = sut.encode(parameters);

        // then
        assertThat(result.getTokenValue()).endsWith(".signature-b64u");

        then(jwtRestTemplate).should().postForObject(
                eq(jwtProperties.signUri()),
                signRequestCaptor.capture(),
                eq(SignResponse.class)
        );

        Map<String, Object> decodedPayload =
                decodeBase64UrlJson(signRequestCaptor.getValue().payloadB64u());

        assertThat(((Number) decodedPayload.get("iat")).longValue())
                .isEqualTo(issuedAt.getEpochSecond());

        assertThat(((Number) decodedPayload.get("exp")).longValue())
                .isEqualTo(expiresAt.getEpochSecond());

        assertThat(decodedPayload).doesNotContainKey("nbf");
    }

    @Test
    @DisplayName("sign response가 null이면 JwtEncodingException을 던진다")
    void encode_shouldThrowJwtEncodingException_whenSignResponseIsNull() {
        // given
        given(jwtRestTemplate.postForObject(
                eq(jwtProperties.signUri()),
                any(SignRequest.class),
                eq(SignResponse.class)
        )).willReturn(null);

        JwtEncoderParameters parameters = JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder()
                        .subject("user@test.com")
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .build()
        );

        // when & then
        assertThatThrownBy(() -> sut.encode(parameters))
                .isInstanceOf(JwtEncodingException.class)
                .hasMessageContaining("JWT sign response is null");
    }

    @Test
    @DisplayName("sign response의 signature가 비어 있으면 JwtEncodingException을 던진다")
    void encode_shouldThrowJwtEncodingException_whenSignatureIsBlank() {
        // given
        given(jwtRestTemplate.postForObject(
                eq(jwtProperties.signUri()),
                any(SignRequest.class),
                eq(SignResponse.class)
        )).willReturn(new SignResponse("test-key:1", "RS256", " "));

        JwtEncoderParameters parameters = JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder()
                        .subject("user@test.com")
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .build()
        );

        // when & then
        assertThatThrownBy(() -> sut.encode(parameters))
                .isInstanceOf(JwtEncodingException.class)
                .hasMessageContaining("JWT signature is empty");
    }

    private Map<String, Object> decodeBase64UrlJson(String encoded) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return objectMapper.readValue(decoded, new TypeReference<>() {});
    }
}