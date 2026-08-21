package org.example.apigateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;

public record GatewayKeyResolvers(
        KeyResolver ip,
        KeyResolver user,
        KeyResolver userOrIp
) {
}
