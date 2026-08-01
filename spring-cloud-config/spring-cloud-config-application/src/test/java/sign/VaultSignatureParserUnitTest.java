package sign;

import org.example.configserver.sign.VaultSignatureParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VaultSignatureParser 단위 테스트")
class VaultSignatureParserUnitTest {

    private final VaultSignatureParser parser = new VaultSignatureParser();

    @Test
    @DisplayName("Vault signature를 JWT signature용 Base64Url 문자열로 변환한다")
    void convertVaultSignatureToBase64UrlSignature() {
        // given
        byte[] rawSignature = "signature-bytes".getBytes(StandardCharsets.UTF_8);
        String base64Signature = Base64.getEncoder().encodeToString(rawSignature);
        String vaultSignature = "vault:v1:" + base64Signature;

        // when
        String result = parser.toBase64Url(vaultSignature);

        // then
        String expected = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawSignature);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Vault signature가 null이면 예외가 발생한다")
    void throwExceptionWhenVaultSignatureIsNull() {
        assertThatThrownBy(() -> parser.toBase64Url(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vault signature");
    }

    @Test
    @DisplayName("Vault signature가 빈 문자열이면 예외가 발생한다")
    void throwExceptionWhenVaultSignatureIsBlank() {
        assertThatThrownBy(() -> parser.toBase64Url(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vault signature");
    }

    @Test
    @DisplayName("Vault signature 형식이 잘못되면 예외가 발생한다")
    void throwExceptionWhenVaultSignatureFormatIsInvalid() {
        assertThatThrownBy(() -> parser.toBase64Url("invalid-signature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Vault signature format");
    }

    @Test
    @DisplayName("Vault signature에서 마지막 콜론 뒤의 값을 signature 본문으로 사용한다")
    void useValueAfterLastColonAsSignatureBody() {
        // given
        byte[] rawSignature = "abc".getBytes(StandardCharsets.UTF_8);
        String base64Signature = Base64.getEncoder().encodeToString(rawSignature);
        String vaultSignature = "vault:v3:" + base64Signature;

        // when
        String result = parser.toBase64Url(vaultSignature);

        // then
        String expected = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawSignature);

        assertThat(result).isEqualTo(expected);
    }
}