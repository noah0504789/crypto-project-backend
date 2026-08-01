package vault;

import org.example.configserver.vault.exception.ConfigInfrastructureException;
import org.example.configserver.vault.exception.VaultKeyNotFoundException;
import org.example.configserver.vault.adapter.out.VaultTransitKeyReader;
import org.example.configserver.vault.dto.VaultTransitPublicKeyInfo;
import org.example.configserver.vault.adapter.out.properties.VaultTransitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultTransitKeyReaderUnitTest {

    @Mock
    private VaultTemplate vaultTemplate;

    private VaultTransitKeyReader reader;

    @BeforeEach
    void setUp() {
        reader = new VaultTransitKeyReader(
                vaultTemplate,
                new VaultTransitProperties("transit/sign/", "transit/keys/")
        );
    }

    @Test
    @DisplayName("Vault Transit key 응답에서 최신 public key 정보를 읽어온다")
    void readLatestPublicKeyInfo() {
        // given
        String keyName = "jwt-key";
        String publicKeyPem = """
                -----BEGIN PUBLIC KEY-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtest
                -----END PUBLIC KEY-----
                """;

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "latest_version", 2,
                "keys", Map.of(
                        "1", Map.of("public_key", "old-public-key"),
                        "2", Map.of("public_key", publicKeyPem)
                )
        ));

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when
        VaultTransitPublicKeyInfo result = reader.readLatestKey(keyName);

        // then
        assertThat(result.keyName()).isEqualTo(keyName);
        assertThat(result.version()).isEqualTo("2");
        assertThat(result.publicKeyPem()).isEqualTo(publicKeyPem);
    }

    @Test
    @DisplayName("VaultTemplate은 transit/keys/{keyName} 경로로 조회한다")
    void readFromTransitKeysPath() {
        // given
        String keyName = "oauth-jwt-key";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "latest_version", "1",
                "keys", Map.of(
                        "1", Map.of("public_key", "public-key-pem")
                )
        ));

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when
        VaultTransitPublicKeyInfo result = reader.readLatestKey(keyName);

        // then
        assertThat(result.keyName()).isEqualTo(keyName);
        assertThat(result.version()).isEqualTo("1");
        assertThat(result.publicKeyPem()).isEqualTo("public-key-pem");
    }

    @Test
    @DisplayName("Vault 응답이 null이면 예외가 발생한다")
    void throwExceptionWhenVaultResponseIsNull() {
        // given
        String keyName = "jwt-key";

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(null);

        // when & then
        assertThatThrownBy(() -> reader.readLatestKey(keyName))
                .isInstanceOf(VaultKeyNotFoundException.class)
                .hasMessageContaining("Vault key response is empty")
                .hasMessageContaining(keyName);
    }

    @Test
    @DisplayName("Vault 응답 data가 null이면 예외가 발생한다")
    void throwExceptionWhenVaultResponseDataIsNull() {
        // given
        String keyName = "jwt-key";

        VaultResponse response = new VaultResponse();

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when & then
        assertThatThrownBy(() -> reader.readLatestKey(keyName))
                .isInstanceOf(VaultKeyNotFoundException.class)
                .hasMessageContaining("Vault key response is empty")
                .hasMessageContaining(keyName);
    }

    @Test
    @DisplayName("keys 필드가 Map이 아니면 예외가 발생한다")
    void throwExceptionWhenKeysFieldIsNotMap() {
        // given
        String keyName = "jwt-key";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "latest_version", 2,
                "keys", "invalid-keys"
        ));

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when & then
        assertThatThrownBy(() -> reader.readLatestKey(keyName))
                .isInstanceOf(ConfigInfrastructureException.class)
                .hasMessageContaining("Vault field is not map")
                .hasMessageContaining("keys");
    }

    @Test
    @DisplayName("최신 버전 metadata가 Map이 아니면 예외가 발생한다")
    void throwExceptionWhenLatestKeyMetadataIsNotMap() {
        // given
        String keyName = "jwt-key";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "latest_version", 2,
                "keys", Map.of(
                        "2", "invalid-meta"
                )
        ));

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when & then
        assertThatThrownBy(() -> reader.readLatestKey(keyName))
                .isInstanceOf(ConfigInfrastructureException.class)
                .hasMessageContaining("Vault field is not map")
                .hasMessageContaining("keys.2");
    }

    @Test
    @DisplayName("latest_version이 문자열이어도 정상 처리한다")
    void readLatestVersionWhenLatestVersionIsString() {
        // given
        String keyName = "jwt-key";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "latest_version", "3",
                "keys", Map.of(
                        "3", Map.of("public_key", "public-key-v3")
                )
        ));

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when
        VaultTransitPublicKeyInfo result = reader.readLatestKey(keyName);

        // then
        assertThat(result.keyName()).isEqualTo(keyName);
        assertThat(result.version()).isEqualTo("3");
        assertThat(result.publicKeyPem()).isEqualTo("public-key-v3");
    }

    @Test
    @DisplayName("latest_version에 해당하는 key metadata가 없으면 예외가 발생한다")
    void throwExceptionWhenLatestVersionMetadataIsMissing() {
        // given
        String keyName = "jwt-key";

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "latest_version", 9,
                "keys", Map.of(
                        "1", Map.of("public_key", "public-key-v1")
                )
        ));

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when & then
        assertThatThrownBy(() -> reader.readLatestKey(keyName))
                .isInstanceOf(ConfigInfrastructureException.class)
                .hasMessageContaining("Vault field is not map")
                .hasMessageContaining("keys.9");
    }

    @Test
    @DisplayName("public_key 값이 그대로 반환된다")
    void returnPublicKeyPemAsIs() {
        // given
        String keyName = "jwt-key";
        String publicKeyPem = """
                -----BEGIN PUBLIC KEY-----
                ABCDEF
                GHIJKL
                -----END PUBLIC KEY-----
                """;

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "latest_version", 1,
                "keys", Map.of(
                        "1", Map.of("public_key", publicKeyPem)
                )
        ));

        when(vaultTemplate.read("transit/keys/" + keyName))
                .thenReturn(response);

        // when
        VaultTransitPublicKeyInfo result = reader.readLatestKey(keyName);

        // then
        assertThat(result.publicKeyPem()).isEqualTo(publicKeyPem);
    }
}
