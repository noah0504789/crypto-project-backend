package org.example.common.id;

import org.example.common.id.properties.IdGenProperties;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;

public class SnowflakeIdGenerator implements BeforeExecutionGenerator {

    private static final long DC_BITS = 5;
    private static final long WK_BITS = 5;
    private static final long SEQ_BITS = 12;

    private static final long MAX_DC = (1L << DC_BITS) - 1;
    private static final long MAX_WK = (1L << WK_BITS) - 1;
    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;

    private static final long WK_SHIFT = SEQ_BITS;
    private static final long DC_SHIFT = SEQ_BITS + WK_BITS;
    private static final long TIME_SHIFT = SEQ_BITS + WK_BITS + DC_BITS;

    private final AtomicLong state = new AtomicLong(0L);

    private long epochMilli;
    private int dcId;
    private int wkId;

    private volatile boolean init = false;

    @Override
    public Object generate(
            SharedSessionContractImplementor session,
            Object owner,
            Object currentValue,
            EventType eventType
    ) {
        if (currentValue != null) {
            return currentValue;
        }

        initFromSpring(session);

        return nextId();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    private void initFromSpring(SharedSessionContractImplementor session) {
        if (init) {
            return;
        }

        synchronized (this) {
            if (init) {
                return;
            }

            ManagedBeanRegistry registry = session.getFactory()
                    .getServiceRegistry()
                    .getService(ManagedBeanRegistry.class);

            if (registry == null) {
                throw new IllegalStateException(
                        "ManagedBeanRegistry not available. Enable Spring container integration."
                );
            }

            IdGenProperties props = registry.getBean(IdGenProperties.class)
                    .getBeanInstance();

            this.epochMilli = props.epoch().toEpochMilli();
            this.dcId = props.datacenterId();
            this.wkId = props.workerId();

            if (epochMilli > System.currentTimeMillis()) {
                throw new IllegalArgumentException("epoch must not be in the future");
            }

            if (dcId < 0 || dcId > MAX_DC) {
                throw new IllegalArgumentException("dcId out of range");
            }

            if (wkId < 0 || wkId > MAX_WK) {
                throw new IllegalArgumentException("workerId out of range");
            }

            this.init = true;
        }
    }

    private Long nextId() {
        while (true) {
            long now = System.currentTimeMillis();

            long currentState = state.get();
            long lastTs = currentState >>> SEQ_BITS;
            long seq = currentState & MAX_SEQ;

            if (now < lastTs) {
                now = waitUntil(lastTs);
            }

            long nextTs = now;
            long nextSeq;

            if (nextTs == lastTs) {
                nextSeq = (seq + 1) & MAX_SEQ;

                if (nextSeq == 0) {
                    nextTs = waitUntil(lastTs + 1);
                }
            } else {
                nextSeq = 0;
            }

            long newState = (nextTs << SEQ_BITS) | nextSeq;

            if (!state.compareAndSet(currentState, newState)) {
                continue;
            }

            return ((nextTs - epochMilli) << TIME_SHIFT) | ((long) dcId << DC_SHIFT) | ((long) wkId << WK_SHIFT) | nextSeq;
        }
    }

    private long waitUntil(long targetMs) {
        long current;

        while ((current = System.currentTimeMillis()) < targetMs) {
            Thread.onSpinWait();
        }

        return current;
    }
}
