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
        Market market,
        Notification notification,
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
    public record Auth(String pattern, String logout, String refresh) {}
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
            String roomPattern,
            String roomMePattern,
            String roomMembersPattern,
            String roomActivityPattern,
            String roomMessagesPattern
    ) {}
    public record User(
            String base,
            String signUp,
            String me,
            String profile
    ) {

        public String signUpPath() {
            return base + signUp;
        }

        public String mePath() {
            return base + me;
        }

        public String profilePath() {
            return base + profile;
        }

        public String profilePattern() {
            return base + "/*/profile";
        }

        public String pattern() {
            return base + "/**";
        }
    }
    public record Market(
            String markets,
            String marketsPattern,
            String priceAlerts,
            String priceAlertsPattern
    ) {}
    public record Notification(
            String notifications,
            String notificationsPattern
    ) {}
    public record Route(
            String userApiVersion,
            String chatApiVersion,
            String marketApiVersion,
            String notificationApiVersion
    ) {}
}
