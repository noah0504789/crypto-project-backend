package org.example.oauth2.validator;

import org.example.common.enums.JwtClaimKey;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class RequiredUserIdClaimValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String userId = token.getClaimAsString(JwtClaimKey.USER_ID.value());

        if (userId != null && !userId.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                        "invalid_token",
                        "Required claim 'id' is missing",
                        null
                )
        );
    }
}
