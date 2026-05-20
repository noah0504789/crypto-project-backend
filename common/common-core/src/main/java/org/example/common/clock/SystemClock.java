package org.example.common.clock;

import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class SystemClock implements Clock {
    @Override
    public long nowMs() {
        return System.currentTimeMillis();
    }

    @Override
    public Instant now() {
        return Instant.now();
    }
}
