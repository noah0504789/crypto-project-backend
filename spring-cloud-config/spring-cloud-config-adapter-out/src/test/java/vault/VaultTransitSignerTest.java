package vault;

import org.example.configserver.vault.adapter.out.dto.VaultSignRequest;
import org.example.configserver.vault.adapter.out.properties.VaultTransitProperties;
import org.example.configserver.vault.dto.VaultTransitSignResult;
import org.example.configserver.vault.adapter.out.VaultTransitSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultTransitSignerTest {

    @Mock
    private VaultTemplate vaultTemplate;

    private VaultTransitSigner sut;

    @BeforeEach
    void setUp() {
        sut = new VaultTransitSigner(
                vaultTemplate,
                new VaultTransitProperties("transit/sign/", "transit/keys/")
        );
    }

    @Test
    @DisplayName("Vault Transit sign API를 호출하고 signature와 key version을 반환한다")
    void signWithVaultTransit() {
        // given
        String keyName = "jwt-key";
        Integer keyVersion = 3;
        String digestB64 = "digest-base64";
        String vaultSignature = "vault:v3:signature-base64";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "signature", vaultSignature,
                "key_version", 3
        ));

        when(vaultTemplate.write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                org.mockito.ArgumentMatchers.any(VaultSignRequest.class)
        )).thenReturn(response);

        // when
        VaultTransitSignResult result = sut.sign(keyName, keyVersion, digestB64);

        // then
        assertThat(result.signature()).isEqualTo(vaultSignature);
        assertThat(result.keyVersion()).isEqualTo("3");
    }

    @Test
    @DisplayName("VaultTemplate.write는 transit/sign/{keyName} 경로로 호출된다")
    void callVaultTransitSignPath() {
        // given
        String keyName = "oauth-jwt-key";
        Integer keyVersion = 1;
        String digestB64 = "digest-base64";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "signature", "vault:v1:signature-base64",
                "key_version", 1
        ));

        when(vaultTemplate.write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                org.mockito.ArgumentMatchers.any(VaultSignRequest.class)
        )).thenReturn(response);

        // when
        sut.sign(keyName, keyVersion, digestB64);

        // then
        verify(vaultTemplate).write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                org.mockito.ArgumentMatchers.any(VaultSignRequest.class)
        );
    }

    @Test
    @DisplayName("Vault sign 요청에는 digest, prehashed, hash_algorithm, signature_algorithm, key_version이 포함된다")
    void createVaultSignRequestCorrectly() {
        // given
        String keyName = "jwt-key";
        Integer keyVersion = 5;
        String digestB64 = "digest-base64";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "signature", "vault:v5:signature-base64",
                "key_version", 5
        ));

        when(vaultTemplate.write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                org.mockito.ArgumentMatchers.any(VaultSignRequest.class)
        )).thenReturn(response);

        ArgumentCaptor<VaultSignRequest> captor =
                ArgumentCaptor.forClass(VaultSignRequest.class);

        // when
        sut.sign(keyName, keyVersion, digestB64);

        // then
        verify(vaultTemplate).write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                captor.capture()
        );

        VaultSignRequest request = captor.getValue();

        assertThat(request.input()).isEqualTo(digestB64);
        assertThat(request.prehashed()).isTrue();
        assertThat(request.hash_algorithm()).isEqualTo("sha2-256");
        assertThat(request.signature_algorithm()).isEqualTo("pkcs1v15");
        assertThat(request.key_version()).isEqualTo(keyVersion);
    }

    @Test
    @DisplayName("Vault 응답이 null이면 예외가 발생한다")
    void throwExceptionWhenVaultResponseIsNull() {
        // given
        String keyName = "jwt-key";

        when(vaultTemplate.write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                org.mockito.ArgumentMatchers.any(VaultSignRequest.class)
        )).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.sign(keyName, 1, "digest-base64"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vault sign response is empty")
                .hasMessageContaining(keyName);
    }

    @Test
    @DisplayName("Vault 응답 data가 null이면 예외가 발생한다")
    void throwExceptionWhenVaultResponseDataIsNull() {
        // given
        String keyName = "jwt-key";

        VaultResponse response = new VaultResponse();

        when(vaultTemplate.write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                org.mockito.ArgumentMatchers.any(VaultSignRequest.class)
        )).thenReturn(response);

        // when & then
        assertThatThrownBy(() -> sut.sign(keyName, 1, "digest-base64"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vault sign response is empty")
                .hasMessageContaining(keyName);
    }

    @Test
    @DisplayName("Vault 응답의 key_version이 문자열이어도 문자열로 반환한다")
    void returnKeyVersionAsStringWhenVaultReturnsString() {
        // given
        String keyName = "jwt-key";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "signature", "vault:v7:signature-base64",
                "key_version", "7"
        ));

        when(vaultTemplate.write(
                org.mockito.ArgumentMatchers.eq("transit/sign/" + keyName),
                org.mockito.ArgumentMatchers.any(VaultSignRequest.class)
        )).thenReturn(response);

        // when
        VaultTransitSignResult result = sut.sign(keyName, 7, "digest-base64");

        // then
        assertThat(result.keyVersion()).isEqualTo("7");
        assertThat(result.signature()).isEqualTo("vault:v7:signature-base64");
    }
}
