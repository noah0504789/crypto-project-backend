package org.example.common.clock;

import java.time.Instant;

public interface Clock {
    long nowMs();
    Instant now();
}
