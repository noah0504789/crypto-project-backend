package org.example.apigateway.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record GatewayRateLimitProperties(
        @NotNull @Valid Bucket signUp,
        @NotNull @Valid Bucket oauth2Authorization,
        @NotNull @Valid Bucket oauth2Callback,
        @NotNull @Valid Bucket tokenRefresh,
        @NotNull @Valid Bucket logout,
        @NotNull @Valid Bucket websocketHandshake,
        @NotNull @Valid Bucket command,
        @NotNull @Valid Bucket query,
        @NotNull @Valid Bucket publicQuery
) {

    public record Bucket(
            @Positive int replenishRate,
            @Positive int burstCapacity,
            @Positive int requestedTokens
    ) {

        @AssertTrue(message = "burstCapacity must be greater than or equal to requestedTokens")
        public boolean isBurstCapacityValid() {
            return burstCapacity >= requestedTokens;
        }
    }
}
