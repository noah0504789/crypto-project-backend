package org.example.apigateway.oauth2.validator;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.apigateway.oauth2.application.service.BlacklistTokenService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ReactiveBlacklistTokenValidator {

    private static final OAuth2Error BLACKLIST_ERROR = new OAuth2Error("invalid_token", "blacklist token", null);

    private final BlacklistTokenService blacklistTokenService;

    public Mono<Jwt> validate(Jwt token) {
        return blacklistTokenService.existsByAccessToken(token.getTokenValue())
                .flatMap(blacklisted -> blacklisted
                        ? Mono.error(new JwtValidationException("blacklist token", List.of(BLACKLIST_ERROR)))
                        : Mono.just(token));
    }
}
