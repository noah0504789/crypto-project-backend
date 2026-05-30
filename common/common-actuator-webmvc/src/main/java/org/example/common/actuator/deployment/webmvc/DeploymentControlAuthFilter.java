package org.example.common.actuator.deployment.webmvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.example.common.actuator.deployment.core.DeploymentControlProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class DeploymentControlAuthFilter extends OncePerRequestFilter {

    private static final String DEPLOY_TOKEN_HEADER = "X-Deploy-Token";
    private static final String DEPLOYMENT_PATH_PREFIX = "/internal/deployment/";

    private final DeploymentControlProperties properties;

    public DeploymentControlAuthFilter(DeploymentControlProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(DEPLOYMENT_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String expectedToken = properties.token();
        String actualToken = request.getHeader(DEPLOY_TOKEN_HEADER);

        if (StringUtils.hasText(expectedToken)
                && StringUtils.hasText(actualToken)
                && expectedToken.equals(actualToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getOutputStream().write("""
                {"message":"Unauthorized deployment control request"}
                """.getBytes(StandardCharsets.UTF_8));
    }
}