package org.example.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class ClockService implements Clock {

    @Override
    public long nowMs() {
        return System.currentTimeMillis();
    }

    @Override
    public long monotonicTimeNanos() {
        return System.nanoTime();
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now(ServiceTimeConverter.ZONE_ID);
    }
}
