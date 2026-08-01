package org.example.common.actuator.deployment.webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.common.actuator.deployment.core.DeploymentControlProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DeploymentControlAuthFilterUnitTest {

    @Test
    @DisplayName("배포 제어 경로가 아니면 필터를 적용하지 않는다")
    void shouldSkipNonDeploymentPath() throws Exception {
        DeploymentControlAuthFilter filter = new DeploymentControlAuthFilter(
                new DeploymentControlProperties("test-token")
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("배포 제어 경로에서 토큰이 없으면 401을 반환한다")
    void shouldRejectDeploymentPathWithoutToken() throws Exception {
        DeploymentControlAuthFilter filter = new DeploymentControlAuthFilter(
                new DeploymentControlProperties("test-token")
        );

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/internal/deployment/ready"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString())
                .contains("Unauthorized deployment control request");
    }

    @Test
    @DisplayName("배포 제어 경로에서 토큰이 틀리면 401을 반환한다")
    void shouldRejectDeploymentPathWithInvalidToken() throws Exception {
        DeploymentControlAuthFilter filter = new DeploymentControlAuthFilter(
                new DeploymentControlProperties("test-token")
        );

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/internal/deployment/ready"
        );
        request.addHeader("X-Deploy-Token", "wrong-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("배포 제어 경로에서 올바른 토큰이면 요청을 통과시킨다")
    void shouldAllowDeploymentPathWithValidToken() throws Exception {
        DeploymentControlAuthFilter filter = new DeploymentControlAuthFilter(
                new DeploymentControlProperties("test-token")
        );

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/internal/deployment/ready"
        );
        request.addHeader("X-Deploy-Token", "test-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("서버에 설정된 기대 토큰이 비어 있으면 요청을 거부한다")
    void shouldRejectWhenExpectedTokenIsBlank() throws Exception {
        DeploymentControlAuthFilter filter = new DeploymentControlAuthFilter(
                new DeploymentControlProperties("")
        );

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/internal/deployment/ready"
        );
        request.addHeader("X-Deploy-Token", "test-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}