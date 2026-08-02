package org.example.notification.adapter.out.cache;

import config.TestNotificationRedisConfig;
import org.example.common.enums.RedisKey;
import org.example.common.test.config.TestBootApplication;
import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest(properties = {"spring.data.redis.repositories.enabled=false"})
@ContextConfiguration(
        classes = {TestBootApplication.class, TestNotificationRedisConfig.class},
        initializers = RedisTestContainerInitializer.class
)
class RedisNotificationAdapterIntegrationTest {

    @Autowired
    private RedisNotificationAdapter sut;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    @Qualifier("masterHashRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @Nested
    @DisplayName("warmUp / findByIds")
    class WarmUpFindTest {

        @Test
        @DisplayName("warmUpAll 후 findByIds로 master를 조회한다(값 라운드트립)")
        void warmUpThenFind() {
            // given
            Notification master = master("n1", "가격 알림");

            // when
            sut.warmUpAll(List.of(master));

            // then
            Map<String, Notification> result = sut.findByIds(Set.of("n1"));

            assertThat(result).containsKey("n1");

            Notification value = result.get("n1");
            assertThat(value.getId()).isEqualTo("n1");
            assertThat(value.getType()).isEqualTo(NotificationType.PRICE_ALERT);
            assertThat(value.getTitle()).isEqualTo("가격 알림");
            assertThat(value.getMessage()).isEqualTo("메시지");
            assertThat(value.getLink()).isEqualTo("http://link");
            assertThat(value.getMessageParts())
                    .containsExactly(
                            NotificationMessagePart.bold("BTC"),
                            NotificationMessagePart.plain(" 상승")
                    );
            assertThat(value.getPayload()).containsEntry("marketCode", "KRW-BTC");
            assertThat(value.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        }

        @Test
        @DisplayName("캐시에 없는 id는 결과 맵에서 제외된다")
        void findByIdsMissExcluded() {
            sut.warmUpAll(List.of(master("n1", "제목1")));

            Map<String, Notification> result = sut.findByIds(Set.of("n1", "n2", "n3"));

            assertThat(result).containsOnlyKeys("n1");
        }

        @Test
        @DisplayName("빈 조회는 캐시에 접근하지 않고 빈 맵을 반환한다")
        void findByIdsEmpty() {
            assertThat(sut.findByIds(Set.of())).isEmpty();
        }

        @Test
        @DisplayName("warmUpAll은 여러 master를 한 번에 적재한다")
        void warmUpAllMultiple() {
            sut.warmUpAll(List.of(master("n1", "제목1"), master("n2", "제목2")));

            Map<String, Notification> result = sut.findByIds(Set.of("n1", "n2"));

            assertThat(result).containsOnlyKeys("n1", "n2");
            assertThat(result.get("n1").getTitle()).isEqualTo("제목1");
            assertThat(result.get("n2").getTitle()).isEqualTo("제목2");
        }

        @Test
        @DisplayName("warmUpAll은 긴 TTL(<= 7일)을 설정한다")
        void warmUpAllSetsLongTtl() {
            sut.warmUpAll(List.of(master("n1", "제목1")));

            Long ttlSeconds = redisTemplate.getExpire(RedisKey.NOTIFICATION_MASTER.keyFor("n1"), TimeUnit.SECONDS);

            assertThat(ttlSeconds).isNotNull();
            assertThat(ttlSeconds).isGreaterThan(0L);
            assertThat(ttlSeconds).isLessThanOrEqualTo(7 * 24 * 60 * 60L);
        }
    }

    @Nested
    @DisplayName("invalidate")
    class InvalidateTest {

        @Test
        @DisplayName("invalidate는 master 캐시를 제거한다")
        void invalidateRemovesMaster() {
            sut.warmUpAll(List.of(master("n1", "제목1")));
            assertThat(sut.findByIds(Set.of("n1"))).containsKey("n1");

            sut.invalidate("n1");

            assertThat(sut.findByIds(Set.of("n1"))).doesNotContainKey("n1");
        }
    }

    private Notification master(String id, String title) {
        return Notification.rehydrate(
                id,
                NotificationType.PRICE_ALERT,
                title,
                "메시지",
                List.of(
                        NotificationMessagePart.bold("BTC"),
                        NotificationMessagePart.plain(" 상승")
                ),
                "http://link",
                Map.of("marketCode", "KRW-BTC"),
                false,
                null,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }
}
