package org.example.common.clock;

import org.springframework.stereotype.Service;

import java.time.Instant;

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
}
