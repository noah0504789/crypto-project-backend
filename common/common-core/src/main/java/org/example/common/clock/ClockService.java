package org.example.common.clock;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.example.common.time.ServiceZoneUtils.ZONE_ID;

@Service
public class ClockService implements Clock {

    @Override
    public long nowMs() {
        return System.currentTimeMillis();
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now(ZONE_ID);
    }
}
