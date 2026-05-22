package org.example.oauth2.token.adapter.out.vault;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.common.enums.JwtHeaderKey;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.token.adapter.out.vault.dto.SignRequest;
import org.example.oauth2.token.adapter.out.vault.dto.SignResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class Rs256JwtEncoder implements JwtEncoder {

    private final Base64.Encoder b64uEncoder = Base64.getUrlEncoder().withoutPadding();
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate jwtRestTemplate;

    @Override
    public Jwt encode(JwtEncoderParameters parameters) throws JwtEncodingException {
        try {
            LinkedHashMap<String, Object> headers = buildHeaders(parameters);
            LinkedHashMap<String, Object> claims = buildClaims(parameters);

            Instant issuedAt = resolveInstant(claims.get("iat"));
            Instant expiresAt = resolveInstant(claims.get("exp"));

            normalizeNumericDateClaims(claims);

            String headerB64u = encodeJson(headers);
            String payloadB64u = encodeJson(claims);
            String sigB64u = requestSignature(headerB64u, payloadB64u);

            String token = headerB64u + "." + payloadB64u + "." + sigB64u;

            return Jwt.withTokenValue(token)
                    .headers(h -> h.putAll(headers))
                    .claims(c -> c.putAll(claims))
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .build();

        } catch (JwtEncodingException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtEncodingException("Failed to encode JWT", e);
        }
    }

    private LinkedHashMap<String, Object> buildHeaders(JwtEncoderParameters parameters) {
        LinkedHashMap<String, Object> headers = new LinkedHashMap<>(parameters.getJwsHeader().getHeaders());

        headers.put("alg", JwtHeaderKey.DEFAULT_ALGORITHM.value());
        headers.put("typ", JwtHeaderKey.DEFAULT_TYPE.value());
        headers.put("kid", getKeyId());

        return headers;
    }

    private LinkedHashMap<String, Object> buildClaims(JwtEncoderParameters parameters) {
        return new LinkedHashMap<>(parameters.getClaims().getClaims());
    }

    private String getKeyId() {
        String keyName = jwtProperties.keyName();
        Integer keyVersion = jwtProperties.keyVersion();

        if (keyName == null || keyName.isBlank()) {
            throw new JwtEncodingException("JWT keyName is empty");
        }

        if (keyVersion == null) {
            throw new JwtEncodingException("JWT keyVersion is null");
        }

        return keyName + ":" + keyVersion;
    }

    private void normalizeNumericDateClaims(Map<String, Object> claims) {
        convertInstantClaimToEpochSecond(claims, "iat");
        convertInstantClaimToEpochSecond(claims, "exp");
        convertInstantClaimToEpochSecond(claims, "nbf");
    }

    private void convertInstantClaimToEpochSecond(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);

        if (value == null) {
            return;
        }

        Instant instant = resolveInstant(value);
        claims.put(claimName, instant.getEpochSecond());
    }

    private Instant resolveInstant(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Instant instant) {
            return instant;
        }

        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }

        if (value instanceof String stringValue) {
            return stringValue.matches("\\d+") ? Instant.ofEpochSecond(Long.parseLong(stringValue)) : Instant.parse(stringValue);
        }

        throw new JwtEncodingException("Unsupported instant claim type: " + value.getClass().getName());
    }

    private String encodeJson(Map<String, Object> values) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(values);
            return b64uEncoder.encodeToString(bytes);
        } catch (JsonProcessingException e) {
            throw new JwtEncodingException("Failed to serialize JWT JSON", e);
        }
    }

    private String requestSignature(String headerB64u, String payloadB64u) {
        SignRequest signRequest = new SignRequest(
                jwtProperties.keyName(),
                jwtProperties.keyVersion(),
                headerB64u,
                payloadB64u
        );

        SignResponse signResponse = jwtRestTemplate.postForObject(
                jwtProperties.signUri(),
                signRequest,
                SignResponse.class
        );

        if (signResponse == null) {
            throw new JwtEncodingException("JWT sign response is null");
        }

        String sigB64u = signResponse.sigB64u();

        if (sigB64u == null || sigB64u.isBlank()) {
            throw new JwtEncodingException("JWT signature is empty");
        }

        return sigB64u;
    }
}
