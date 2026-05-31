package org.example.apigateway.oauth2.validator;

import lombok.RequiredArgsConstructor;
import org.example.apigateway.oauth2.application.service.BlacklistTokenService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

@RequiredArgsConstructor
public class BlacklistTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final BlacklistTokenService blacklistTokenService;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String accessToken = token.getTokenValue();

        if (!blacklistTokenService.existsByAccessToken(accessToken)) return OAuth2TokenValidatorResult.success();

        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "blacklist token", null));
    }
}
