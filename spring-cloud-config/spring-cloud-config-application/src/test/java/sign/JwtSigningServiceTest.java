package sign;

import org.example.configserver.sign.JwtSigningInputDigester;
import org.example.configserver.sign.JwtSigningService;
import org.example.configserver.sign.VaultSignatureParser;
import org.example.configserver.sign.dto.SignRequest;
import org.example.configserver.sign.dto.SignResponse;
import org.example.configserver.vault.domain.VaultTransitSignResult;
import org.example.configserver.vault.port.out.VaultTransitSignPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtSigningServiceTest {

    @Mock
    private JwtSigningInputDigester jwtSigningInputDigester;

    @Mock
    private VaultTransitSignPort vaultTransitSigner;

    @Mock
    private VaultSignatureParser vaultSignatureParser;

    @InjectMocks
    private JwtSigningService sut;

    @Test
    @DisplayName("JWT 서명 요청을 처리하고 SignResponse를 반환한다")
    void sign() {
        // given
        SignRequest request = new SignRequest(
                "jwt-key",
                3,
                "header-b64u",
                "payload-b64u"
        );

        String digestB64 = "digest-base64";
        VaultTransitSignResult vaultResult = new VaultTransitSignResult(
                "vault:v3:signature-base64",
                "3"
        );
        String sigB64u = "signature-base64url";

        when(jwtSigningInputDigester.digestToBase64(
                request.headerB64u(),
                request.payloadB64u()
        )).thenReturn(digestB64);

        when(vaultTransitSigner.sign(
                request.keyName(),
                request.keyVersion(),
                digestB64
        )).thenReturn(vaultResult);

        when(vaultSignatureParser.toBase64Url(vaultResult.signature()))
                .thenReturn(sigB64u);

        // when
        SignResponse response = sut.sign(request);

        // then
        assertThat(response.kid()).isEqualTo("jwt-key:3");
        assertThat(response.alg()).isEqualTo("RS256");
        assertThat(response.sigB64u()).isEqualTo(sigB64u);
    }

    @Test
    @DisplayName("header와 payload를 digest로 변환한 뒤 Vault 서명 요청에 사용한다")
    void useDigestAsVaultSignInput() {
        // given
        SignRequest request = new SignRequest(
                "jwt-key",
                5,
                "header-b64u",
                "payload-b64u"
        );

        String digestB64 = "digest-base64";
        VaultTransitSignResult vaultResult = new VaultTransitSignResult(
                "vault:v5:signature-base64",
                "5"
        );

        when(jwtSigningInputDigester.digestToBase64("header-b64u", "payload-b64u"))
                .thenReturn(digestB64);

        when(vaultTransitSigner.sign("jwt-key", 5, digestB64))
                .thenReturn(vaultResult);

        when(vaultSignatureParser.toBase64Url("vault:v5:signature-base64"))
                .thenReturn("signature-base64url");

        // when
        sut.sign(request);

        // then
        verify(jwtSigningInputDigester).digestToBase64(
                "header-b64u",
                "payload-b64u"
        );

        verify(vaultTransitSigner).sign(
                "jwt-key",
                5,
                digestB64
        );
    }

    @Test
    @DisplayName("Vault signature를 Base64Url signature로 변환해 응답에 담는다")
    void convertVaultSignatureToBase64UrlSignature() {
        // given
        SignRequest request = new SignRequest(
                "jwt-key",
                1,
                "header",
                "payload"
        );

        VaultTransitSignResult vaultResult = new VaultTransitSignResult(
                "vault:v1:signature-base64",
                "1"
        );

        when(jwtSigningInputDigester.digestToBase64("header", "payload"))
                .thenReturn("digest-base64");

        when(vaultTransitSigner.sign("jwt-key", 1, "digest-base64"))
                .thenReturn(vaultResult);

        when(vaultSignatureParser.toBase64Url("vault:v1:signature-base64"))
                .thenReturn("signature-base64url");

        // when
        SignResponse response = sut.sign(request);

        // then
        verify(vaultSignatureParser).toBase64Url("vault:v1:signature-base64");
        assertThat(response.sigB64u()).isEqualTo("signature-base64url");
    }

    @Test
    @DisplayName("kid는 요청 keyName과 Vault 응답 keyVersion을 조합해 생성한다")
    void createKidFromRequestKeyNameAndVaultKeyVersion() {
        // given
        SignRequest request = new SignRequest(
                "jwt-key",
                2,
                "header",
                "payload"
        );

        VaultTransitSignResult vaultResult = new VaultTransitSignResult(
                "vault:v9:signature-base64",
                "9"
        );

        when(jwtSigningInputDigester.digestToBase64("header", "payload"))
                .thenReturn("digest-base64");

        when(vaultTransitSigner.sign("jwt-key", 2, "digest-base64"))
                .thenReturn(vaultResult);

        when(vaultSignatureParser.toBase64Url("vault:v9:signature-base64"))
                .thenReturn("signature-base64url");

        // when
        SignResponse response = sut.sign(request);

        // then
        assertThat(response.kid()).isEqualTo("jwt-key:9");
    }

    @Test
    @DisplayName("digest 생성, Vault 서명, signature 변환 순서로 처리한다")
    void processInOrder() {
        // given
        SignRequest request = new SignRequest(
                "jwt-key",
                3,
                "header",
                "payload"
        );

        VaultTransitSignResult vaultResult = new VaultTransitSignResult(
                "vault:v3:signature-base64",
                "3"
        );

        when(jwtSigningInputDigester.digestToBase64("header", "payload"))
                .thenReturn("digest-base64");

        when(vaultTransitSigner.sign("jwt-key", 3, "digest-base64"))
                .thenReturn(vaultResult);

        when(vaultSignatureParser.toBase64Url("vault:v3:signature-base64"))
                .thenReturn("signature-base64url");

        // when
        sut.sign(request);

        // then
        var inOrder = inOrder(
                jwtSigningInputDigester,
                vaultTransitSigner,
                vaultSignatureParser
        );

        inOrder.verify(jwtSigningInputDigester)
                .digestToBase64("header", "payload");

        inOrder.verify(vaultTransitSigner)
                .sign("jwt-key", 3, "digest-base64");

        inOrder.verify(vaultSignatureParser)
                .toBase64Url("vault:v3:signature-base64");
    }
}