package org.example.oauth2.token.port;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface AccessTokenStore {

    Duration getTTL();

    String findValue(String clientRegistrationId, String username);

    Map<String, String> findClaims(String accessToken);

    boolean existsByTokenKey(String tokenKey);

    boolean existsByClaimKey(String claimKey);

    List<String> getClaimFlattenMap(Map<String, String> claims);
}
