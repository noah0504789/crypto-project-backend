package org.example.apigateway.oauth2.validator;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class BlacklistAwareReactiveJwtDecoder implements ReactiveJwtDecoder {

    private final ReactiveJwtDecoder delegate;
    private final ReactiveBlacklistTokenValidator blacklistTokenValidator;

    @Override
    public Mono<Jwt> decode(String token) {
        return delegate.decode(token)
                .flatMap(blacklistTokenValidator::validate);
    }
}
