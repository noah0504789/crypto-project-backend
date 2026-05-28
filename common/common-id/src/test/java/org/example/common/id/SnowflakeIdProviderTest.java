package org.example.common.id;

import org.example.common.id.properties.IdGenProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdProviderTest {

    private static final long SEQ_BITS = 12;
    private static final long WK_BITS = 5;
    private static final long DC_BITS = 5;

    private static final long WK_SHIFT = SEQ_BITS;
    private static final long DC_SHIFT = SEQ_BITS + WK_BITS;
    private static final long TIME_SHIFT = SEQ_BITS + WK_BITS + DC_BITS;

    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;
    private static final long MAX_WK = (1L << WK_BITS) - 1;
    private static final long MAX_DC = (1L << DC_BITS) - 1;

    @AfterEach
    void tearDown() {
        resetProviderInstance();
    }

    @Test
    @DisplayName("초기화 후 Snowflake ID를 생성한다")
    void nextId_createsSnowflakeId() {
        // given
        int datacenterId = 3;
        int workerId = 7;
        Instant epoch = Instant.now().minusSeconds(60);

        initializeProvider(epoch, datacenterId, workerId);

        // when
        Long id = SnowflakeIdProvider.nextId();

        // then
        assertThat(id).isNotNull();
        assertThat(id).isPositive();

        assertThat(extractDatacenterId(id)).isEqualTo(datacenterId);
        assertThat(extractWorkerId(id)).isEqualTo(workerId);
        assertThat(extractSequence(id)).isBetween(0L, MAX_SEQ);
        assertThat(extractTimestampPart(id)).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("여러 ID를 생성해도 중복되지 않는다")
    void nextId_createsUniqueIds() {
        // given
        initializeProvider(
                Instant.now().minusSeconds(60),
                1,
                2
        );

        Set<Long> ids = new HashSet<>();

        // when
        for (int i = 0; i < 1_000; i++) {
            ids.add(SnowflakeIdProvider.nextId());
        }

        // then
        assertThat(ids).hasSize(1_000);
    }

    @Test
    @DisplayName("생성된 ID는 대체로 증가한다")
    void nextId_createsIncreasingIds() {
        // given
        initializeProvider(
                Instant.now().minusSeconds(60),
                1,
                1
        );

        // when
        Long first = SnowflakeIdProvider.nextId();
        Long second = SnowflakeIdProvider.nextId();
        Long third = SnowflakeIdProvider.nextId();

        // then
        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
    }

    @Test
    @DisplayName("datacenterId가 허용 범위를 넘으면 예외가 발생한다")
    void init_throwsExceptionWhenDatacenterIdIsOutOfRange() {
        // when & then
        assertThatThrownBy(() -> initializeProvider(
                Instant.now().minusSeconds(60),
                32,
                1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datacenter");
    }

    @Test
    @DisplayName("workerId가 허용 범위를 넘으면 예외가 발생한다")
    void init_throwsExceptionWhenWorkerIdIsOutOfRange() {
        // when & then
        assertThatThrownBy(() -> initializeProvider(
                Instant.now().minusSeconds(60),
                1,
                32
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker");
    }

    @Test
    @DisplayName("epoch가 미래이면 예외가 발생한다")
    void init_throwsExceptionWhenEpochIsFuture() {
        // when & then
        assertThatThrownBy(() -> initializeProvider(
                Instant.now().plusSeconds(60),
                1,
                1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epoch");
    }

    @Test
    @DisplayName("Provider가 초기화되지 않으면 예외가 발생한다")
    void nextId_throwsExceptionWhenProviderIsNotInitialized() {
        // when & then
        assertThatThrownBy(SnowflakeIdProvider::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SnowflakeIdProvider is not initialized");
    }

    @Test
    @DisplayName("datacenterId와 workerId의 최대 허용값은 31이다")
    void init_allowsMaxDatacenterIdAndWorkerId() {
        // given
        int datacenterId = (int) MAX_DC;
        int workerId = (int) MAX_WK;

        initializeProvider(
                Instant.now().minusSeconds(60),
                datacenterId,
                workerId
        );

        // when
        Long id = SnowflakeIdProvider.nextId();

        // then
        assertThat(extractDatacenterId(id)).isEqualTo(datacenterId);
        assertThat(extractWorkerId(id)).isEqualTo(workerId);
    }

    private void initializeProvider(
            Instant epoch,
            int datacenterId,
            int workerId
    ) {
        IdGenProperties properties = new IdGenProperties(
                epoch,
                datacenterId,
                workerId
        );

        SnowflakeIdProvider provider = new SnowflakeIdProvider(properties);
        provider.init();
    }

    private void resetProviderInstance() {
        try {
            Field instanceField = SnowflakeIdProvider.class.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reset SnowflakeIdProvider.INSTANCE", e);
        }
    }

    private long extractTimestampPart(long id) {
        return id >>> TIME_SHIFT;
    }

    private long extractDatacenterId(long id) {
        return (id >>> DC_SHIFT) & MAX_DC;
    }

    private long extractWorkerId(long id) {
        return (id >>> WK_SHIFT) & MAX_WK;
    }

    private long extractSequence(long id) {
        return id & MAX_SEQ;
    }
}