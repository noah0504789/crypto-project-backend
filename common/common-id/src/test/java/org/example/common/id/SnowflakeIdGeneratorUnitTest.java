package org.example.common.id;

import org.example.common.id.properties.IdGenProperties;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    private static final long SEQ_BITS = 12;
    private static final long WK_BITS = 5;
    private static final long DC_BITS = 5;

    private static final long WK_SHIFT = SEQ_BITS;
    private static final long DC_SHIFT = SEQ_BITS + WK_BITS;
    private static final long TIME_SHIFT = SEQ_BITS + WK_BITS + DC_BITS;

    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;
    private static final long MAX_DC = (1L << DC_BITS) - 1;

    @AfterEach
    void tearDown() {
        resetProviderInstance();
    }

    @Test
    @DisplayName("currentValue가 있으면 기존 값을 그대로 반환한다")
    void generate_returnsCurrentValueWhenCurrentValueExists() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        Long currentValue = 123L;

        // when
        Object result = generator.generate(
                null,
                new Object(),
                currentValue,
                EventType.INSERT
        );

        // then
        assertThat(result).isEqualTo(currentValue);
    }

    @Test
    @DisplayName("currentValue가 없으면 SnowflakeIdProvider를 통해 ID를 생성한다")
    void generate_createsSnowflakeIdWhenCurrentValueIsNull() {
        // given
        int datacenterId = 3;
        int workerId = 7;

        initializeProvider(
                Instant.now().minusSeconds(60),
                datacenterId,
                workerId
        );

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        // when
        Long id = (Long) generator.generate(
                null,
                new Object(),
                null,
                EventType.INSERT
        );

        // then
        assertThat(id).isNotNull();
        assertThat(id).isPositive();

        assertThat(extractDatacenterId(id)).isEqualTo(datacenterId);
        assertThat(extractWorkerId(id)).isEqualTo(workerId);
        assertThat(extractSequence(id)).isBetween(0L, MAX_SEQ);
        assertThat(extractTimestampPart(id)).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Provider가 초기화되지 않은 상태에서 currentValue가 없으면 예외가 발생한다")
    void generate_throwsExceptionWhenProviderIsNotInitialized() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        // when & then
        assertThatThrownBy(() -> generator.generate(
                null,
                new Object(),
                null,
                EventType.INSERT
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SnowflakeIdProvider is not initialized");
    }

    @Test
    @DisplayName("지원 이벤트 타입은 INSERT다")
    void getEventTypes_returnsInsertOnly() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        // when & then
        assertThat(generator.getEventTypes())
                .containsExactly(EventType.INSERT);
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
        return (id >>> WK_SHIFT) & ((1L << WK_BITS) - 1);
    }

    private long extractSequence(long id) {
        return id & MAX_SEQ;
    }
}