package org.example.common.actuator.deployment.webflux;

import org.example.common.actuator.deployment.core.DeploymentControlProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class DeploymentControlAuthWebFilter implements WebFilter {

    private static final String DEPLOY_TOKEN_HEADER = "X-Deploy-Token";
    private static final String DEPLOYMENT_PATH_PREFIX = "/internal/deployment/";

    private final DeploymentControlProperties properties;

    public DeploymentControlAuthWebFilter(DeploymentControlProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!path.startsWith(DEPLOYMENT_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        String expectedToken = properties.token();
        String actualToken = exchange.getRequest()
                .getHeaders()
                .getFirst(DEPLOY_TOKEN_HEADER);

        if (StringUtils.hasText(expectedToken)
                && StringUtils.hasText(actualToken)
                && expectedToken.equals(actualToken)) {
            return chain.filter(exchange);
        }

        byte[] body = """
                {"message":"Unauthorized deployment control request"}
                """.getBytes(StandardCharsets.UTF_8);

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body))
        );
    }
}