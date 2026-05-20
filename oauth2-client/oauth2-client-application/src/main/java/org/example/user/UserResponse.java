package org.example.user;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.*;

@Builder
public record UserResponse(
        String id,
        String sub,
        String nickname,
        String email,
        List<String> roles,
        LocalDateTime createdAt
) {
    public Map<String, Object> getAttributes(String clientRegistrationId) {
        HashMap<String, Object> attributes = new HashMap<>();

        attributes.put("id", id);
        attributes.put("sub", sub);
        attributes.put("email", email);
        attributes.put("nickname", nickname);
        attributes.put("clientRegistrationId", clientRegistrationId);
        attributes.put("createdAt", createdAt);

        return attributes;
    }
}
