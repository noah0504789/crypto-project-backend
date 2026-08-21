package org.example.apigateway.ratelimit;

public final class RateLimitedRouteId {

    public static final String OAUTH2_AUTHORIZATION = "oauth2-authorization-route";
    public static final String OAUTH2_CALLBACK = "oauth2-callback-route";
    public static final String TOKEN_REFRESH = "token-refresh-route";
    public static final String LOGOUT = "logout-route";
    public static final String USER_SIGN_UP = "user-sign-up-route";
    public static final String USER_COMMAND = "user-command-route";
    public static final String USER_QUERY = "user-query-route";
    public static final String WEBSOCKET_NATIVE_HANDSHAKE = "ws-native-upgrade";
    public static final String WEBSOCKET_HANDSHAKE = "ws-upgrade";
    public static final String CHAT_COMMAND = "chat-command-route";
    public static final String CHAT_QUERY = "chat-query-route";
    public static final String CHAT_PUBLIC_QUERY = "chat-public-query-route";
    public static final String MARKET_COMMAND = "market-command-route";
    public static final String MARKET_QUERY = "market-query-route";
    public static final String MARKET_PUBLIC_QUERY = "market-public-query-route";
    public static final String NOTIFICATION_COMMAND = "notification-command-route";
    public static final String NOTIFICATION_QUERY = "notification-query-route";

    private RateLimitedRouteId() {
    }
}
