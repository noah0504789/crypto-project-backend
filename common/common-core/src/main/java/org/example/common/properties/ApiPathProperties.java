package org.example.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api-path")
public record ApiPathProperties(
        Websocket websocket,
        Stomp stomp,
        Auth auth,
        OAuth2 oauth2,
        Chat chat,
        User user,
        Route route,
        String optionsPattern,
        String actuatorPattern
) {
    public record Websocket(
            String sockJsEndpoint,
            String nativeEndpoint,
            String prefix,
            String nativePath,
            String nativePattern,
            String pattern,
            String msgPattern,
            String infoPattern
    ) {}
    public record Stomp(String applicationDestinationPrefix, String userDestinationPrefix) {}
    public record Auth(String pattern, String logout) {}
    public record OAuth2(
            String authorizationBaseUri,
            String authorizationPattern,
            String loginCallbackPattern,
            String loginCallbackBaseUri,
            String loginFailureRedirectUri,
            String pattern
    ) {}
    public record Chat(
            String base,
            String roomsPopular,
            String roomsMe,
            String room,
            String roomMe,
            String roomMembers,
            String roomActivity,
            String roomCreate,
            String roomMessages,
            String pattern,
            String roomMePattern,
            String roomMembersPattern,
            String roomActivityPattern,
            String roomMessagesPattern
    ) {}
    public record User(
            String pattern,
            String me,
            String profilePattern
    ) {}
    public record Route(
            String userApiVersion,
            String chatApiVersion
    ) {}
}
