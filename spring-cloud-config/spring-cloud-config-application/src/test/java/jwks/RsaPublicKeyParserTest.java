package jwks;

import org.example.jwks.RsaPublicKeyParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RsaPublicKeyParser 단위 테스트")
class RsaPublicKeyParserTest {

    private final RsaPublicKeyParser parser = new RsaPublicKeyParser(createRsaKeyFactory());

    @Test
    @DisplayName("PEM 형식의 public key를 RSAPublicKey로 변환한다")
    void parsePemPublicKeyToRsaPublicKey() throws Exception {
        // given
        KeyPair keyPair = createRsaKeyPair();
        RSAPublicKey originalPublicKey = (RSAPublicKey) keyPair.getPublic();

        String publicKeyPem = toPem(originalPublicKey);

        // when
        RSAPublicKey result = parser.parse(publicKeyPem);

        // then
        assertThat(result.getModulus()).isEqualTo(originalPublicKey.getModulus());
        assertThat(result.getPublicExponent()).isEqualTo(originalPublicKey.getPublicExponent());
    }

    @Test
    @DisplayName("PEM에 개행과 공백이 있어도 RSAPublicKey로 변환한다")
    void parsePemPublicKeyWithWhitespaces() throws Exception {
        // given
        KeyPair keyPair = createRsaKeyPair();
        RSAPublicKey originalPublicKey = (RSAPublicKey) keyPair.getPublic();

        String publicKeyPem = toPem(originalPublicKey)
                .replace("-----BEGIN PUBLIC KEY-----", "-----BEGIN PUBLIC KEY-----\n\n")
                .replace("-----END PUBLIC KEY-----", "\n\n-----END PUBLIC KEY-----");

        // when
        RSAPublicKey result = parser.parse(publicKeyPem);

        // then
        assertThat(result.getModulus()).isEqualTo(originalPublicKey.getModulus());
        assertThat(result.getPublicExponent()).isEqualTo(originalPublicKey.getPublicExponent());
    }

    @Test
    @DisplayName("잘못된 PEM 문자열이면 예외가 발생한다")
    void throwExceptionWhenPemIsInvalid() {
        assertThatThrownBy(() -> parser.parse("invalid-pem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse RSA public key");
    }

    @Test
    @DisplayName("PEM 값이 null이면 예외가 발생한다")
    void throwExceptionWhenPemIsNull() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse RSA public key");
    }

    @Test
    @DisplayName("Base64는 맞지만 RSA public key 형식이 아니면 예외가 발생한다")
    void throwExceptionWhenDecodedBytesAreNotRsaPublicKey() {
        // given
        String invalidBase64 = Base64.getEncoder().encodeToString("not-rsa-key".getBytes());

        String publicKeyPem = """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----
                """.formatted(invalidBase64);

        // when & then
        assertThatThrownBy(() -> parser.parse(publicKeyPem))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse RSA public key");
    }

    private static KeyFactory createRsaKeyFactory() {
        try {
            return KeyFactory.getInstance("RSA");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair createRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPem(RSAPublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(publicKey.getEncoded());

        return """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----
                """.formatted(base64);
    }
}