package org.example.common.time;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;


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
        return LocalDateTime.now(ServiceTimeConverter.ZONE_ID);
    }
}
