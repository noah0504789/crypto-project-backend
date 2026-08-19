package org.example.common.id;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.common.id.properties.IdGenProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

// snowflake ID를 실제로 쓰는 서비스(idgen 설정을 로드하는 서비스)에서만 등록한다.
// idgen 미설정 서비스가 common-id를 넓게 스캔해도 @PostConstruct init이 돌지 않아 부팅이 깨지지 않는다.
@Component
@ConditionalOnProperty(prefix = "idgen", name = "epoch")
@RequiredArgsConstructor
public class SnowflakeIdProvider {

    private static SnowflakeIdProvider INSTANCE;

    private static final long DC_BITS = 5;
    private static final long WK_BITS = 5;
    private static final long SEQ_BITS = 12;

    private static final long MAX_DC = (1L << DC_BITS) - 1;
    private static final long MAX_WK = (1L << WK_BITS) - 1;
    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;

    private static final long WK_SHIFT = SEQ_BITS;
    private static final long DC_SHIFT = SEQ_BITS + WK_BITS;
    private static final long TIME_SHIFT = SEQ_BITS + WK_BITS + DC_BITS;

    private final IdGenProperties properties;

    private final AtomicLong state = new AtomicLong(0L);

    private long epochMilli;
    private int datacenterId;
    private int workerId;

    @PostConstruct
    public void init() {
        this.epochMilli = properties.epoch().toEpochMilli();
        this.datacenterId = properties.datacenterId();
        this.workerId = properties.workerId();

        validate();

        INSTANCE = this;
    }

    public static Long nextId() {
        return getInstance().generateNextId();
    }

    private static SnowflakeIdProvider getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "SnowflakeIdProvider is not initialized. " +
                            "Check @ComponentScan, @ConfigurationPropertiesScan, and idgen properties."
            );
        }

        return INSTANCE;
    }

    private Long generateNextId() {
        while (true) {
            long now = System.currentTimeMillis();

            long currentState = state.get();
            long lastTimestamp = currentState >>> SEQ_BITS;
            long sequence = currentState & MAX_SEQ;

            if (now < lastTimestamp) {
                now = waitUntil(lastTimestamp);
            }

            long nextTimestamp = now;
            long nextSequence;

            if (nextTimestamp == lastTimestamp) {
                nextSequence = (sequence + 1) & MAX_SEQ;

                if (nextSequence == 0) {
                    nextTimestamp = waitUntil(lastTimestamp + 1);
                }
            } else {
                nextSequence = 0;
            }

            long newState = (nextTimestamp << SEQ_BITS) | nextSequence;

            if (!state.compareAndSet(currentState, newState)) {
                continue;
            }

            return ((nextTimestamp - epochMilli) << TIME_SHIFT)
                    | ((long) datacenterId << DC_SHIFT)
                    | ((long) workerId << WK_SHIFT)
                    | nextSequence;
        }
    }

    private void validate() {
        if (epochMilli > System.currentTimeMillis()) {
            throw new IllegalArgumentException("idgen.epoch must not be in the future");
        }

        if (datacenterId < 0 || datacenterId > MAX_DC) {
            throw new IllegalArgumentException("idgen.datacenter-id out of range: 0 ~ " + MAX_DC);
        }

        if (workerId < 0 || workerId > MAX_WK) {
            throw new IllegalArgumentException("idgen.worker-id out of range: 0 ~ " + MAX_WK);
        }
    }

    private long waitUntil(long targetMillis) {
        long currentMillis;

        while ((currentMillis = System.currentTimeMillis()) < targetMillis) {
            Thread.onSpinWait();
        }

        return currentMillis;
    }
}
