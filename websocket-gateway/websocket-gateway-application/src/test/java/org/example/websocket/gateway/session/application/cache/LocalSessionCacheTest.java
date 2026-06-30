package org.example.websocket.gateway.session.application.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("LocalSessionCache")
class LocalSessionCacheTest {

    private LocalSessionCache sut;

    private final String userId = "user-1";
    private final String otherUserId = "user-2";

    private final String sessionId = "session-1";
    private final String otherSessionId = "session-2";

    @BeforeEach
    void setUp() {
        sut = new LocalSessionCache();
    }

    @Test
    @DisplayName("세션을 등록하면 sessionId로 userId를 조회할 수 있다")
    void registerAndFindUserId() {
        // when
        sut.register(sessionId, userId);

        // then
        assertThat(sut.findUserId(sessionId)).isEqualTo(userId);
    }

    @Test
    @DisplayName("등록되지 않은 sessionId는 null을 반환한다")
    void findUserIdWhenSessionDoesNotExist() {
        // when
        String result = sut.findUserId(sessionId);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("세션을 등록하면 해당 userId는 접속 중인 사용자로 판단한다")
    void hasUserAfterRegister() {
        // when
        sut.register(sessionId, userId);

        // then
        assertThat(sut.hasUser(userId)).isTrue();
    }

    @Test
    @DisplayName("등록되지 않은 userId는 접속 중이 아니라고 판단한다")
    void hasUserWhenUserDoesNotExist() {
        // when
        boolean result = sut.hasUser(userId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("세션을 제거하면 sessionId로 userId를 조회할 수 없다")
    void removeSession() {
        // given
        sut.register(sessionId, userId);

        // when
        sut.remove(sessionId);

        // then
        assertThat(sut.findUserId(sessionId)).isNull();
        assertThat(sut.hasUser(userId)).isFalse();
    }

    @Test
    @DisplayName("한 사용자가 여러 세션을 가지고 있으면 하나를 제거해도 사용자는 접속 중으로 남는다")
    void removeOneSessionWhenUserHasMultipleSessions() {
        // given
        sut.register(sessionId, userId);
        sut.register(otherSessionId, userId);

        // when
        sut.remove(sessionId);

        // then
        assertThat(sut.findUserId(sessionId)).isNull();
        assertThat(sut.findUserId(otherSessionId)).isEqualTo(userId);
        assertThat(sut.hasUser(userId)).isTrue();
    }

    @Test
    @DisplayName("한 사용자의 마지막 세션을 제거하면 userId도 접속 중이 아니게 된다")
    void removeLastSessionForUser() {
        // given
        sut.register(sessionId, userId);
        sut.register(otherSessionId, userId);

        // when
        sut.remove(sessionId);
        sut.remove(otherSessionId);

        // then
        assertThat(sut.findUserId(sessionId)).isNull();
        assertThat(sut.findUserId(otherSessionId)).isNull();
        assertThat(sut.hasUser(userId)).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 sessionId를 제거해도 예외가 발생하지 않는다")
    void removeUnknownSession() {
        // when & then
        assertDoesNotThrow(() -> sut.remove(sessionId));

        assertThat(sut.hasUser(userId)).isFalse();
    }

    @Test
    @DisplayName("서로 다른 사용자의 세션은 독립적으로 관리된다")
    void manageDifferentUsersIndependently() {
        // given
        sut.register(sessionId, userId);
        sut.register(otherSessionId, otherUserId);

        // when
        sut.remove(sessionId);

        // then
        assertThat(sut.hasUser(userId)).isFalse();
        assertThat(sut.hasUser(otherUserId)).isTrue();
        assertThat(sut.findUserId(otherSessionId)).isEqualTo(otherUserId);
    }

    @Test
    @DisplayName("같은 sessionId를 다른 userId로 다시 등록하면 sessionToUser는 최신 userId로 갱신된다")
    void registerSameSessionIdWithDifferentUser() {
        // given
        sut.register(sessionId, userId);

        // when
        sut.register(sessionId, otherUserId);

        // then
        assertThat(sut.findUserId(sessionId)).isEqualTo(otherUserId);
        assertThat(sut.hasUser(otherUserId)).isTrue();

        // 현재 구현상 기존 userId의 userToSessions에는 sessionId가 남을 수 있음
        assertThat(sut.hasUser(userId)).isTrue();
    }
}