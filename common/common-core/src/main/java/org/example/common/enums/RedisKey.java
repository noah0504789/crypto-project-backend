package org.example.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.common.exception.InfrastructureException;

@Getter
@AllArgsConstructor
public enum RedisKey {

    CHAT_ROOM_INFO("{chat}:room:%s", 1),
    CHAT_ROOM_LAST_READ_SEQ("{chat}:room:%s:last_read", 1),
    CHAT_ROOM_TITLE_UNIQUE_INDEX("{chat}:room:title:idx", 0),
    CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX("{chat}:popular-room:%s", 1),
    CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX("{chat}:active-room:%s", 1),
    CHAT_ROOM_ACTIVITY_RECENT_INDEX("{chat}:room:activity:recent", 0),
    CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX("{chat}:room:activity:inflight", 0),

    CHAT_MESSAGE_INFO("{chat}:message:%s", 1),
    CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX("{chat}:room:%s:message-access", 1),

    // Notification (info only, warm-up cache)
    NOTIFICATION_INFO("{noti}:info:%s", 1),

    // Auth
    ACCESS_TOKEN("{auth}:accesstoken:%s:%s", 2),
    ACCESS_CLAIMS("{auth}:claims:%s", 1),
    REFRESH_TOKEN("{auth}:refreshtoken:%s:%s", 2),
    REFRESH_EMAIL_PREFIX("{auth}:refreshtoken:%s:", 1),
    TOKENS_SET("{auth}:tokens:%s", 1),
    BLACKLIST_TOKEN_SET("{auth}:blacklist", 0),

    // Session
    SESSION_INFO("{session}:user:%s", 1);

    private final String pattern;
    private final int expectedArgCount;

    public String keyFor(String... args) {
        validateArgCount(args);

        if (expectedArgCount == 0) {
            return pattern;
        }

        return String.format(pattern, (Object[]) args);
    }

    public String extractIdentifier(String key) {
        String prefix = identifierPrefix();
        String suffix = identifierSuffix();

        if (!key.startsWith(prefix) || !key.endsWith(suffix)) {
            throw new InfrastructureException(
                    "Key does not match pattern. pattern=" + pattern + ", key=" + key
            );
        }

        return key.substring(prefix.length(), key.length() - suffix.length());
    }

    /**
     * 식별자 앞뒤 고정 문자열. Lua 안에서 멤버마다 키를 조립해야 하는 경우
     * (예: 방 하나의 projection 이 멤버 N 명의 key 를 만든다) 패턴 정의처를 이 enum 하나로
     * 유지하기 위해 스크립트에 넘긴다.
     */
    public String identifierPrefix() {
        validateExtractable();

        return pattern.substring(0, pattern.indexOf("%s"));
    }

    public String identifierSuffix() {
        validateExtractable();

        return pattern.substring(pattern.indexOf("%s") + 2);
    }

    private void validateArgCount(String... args) {
        int actual = args == null ? 0 : args.length;

        if (actual != expectedArgCount) {
            throw new InfrastructureException(
                    "Invalid Redis key arguments. key=" + name()
                            + ", expected=" + expectedArgCount
                            + ", actual=" + actual
                            + ", pattern=" + pattern
            );
        }
    }

    private void validateExtractable() {
        int firstIndex = pattern.indexOf("%s");

        if (firstIndex < 0) {
            throw new InfrastructureException(
                    "This RedisKey has no placeholder. key=" + name()
            );
        }

        int lastIndex = pattern.lastIndexOf("%s");

        if (firstIndex != lastIndex) {
            throw new InfrastructureException(
                    "This RedisKey has multiple placeholders. key=" + name()
            );
        }
    }
}
