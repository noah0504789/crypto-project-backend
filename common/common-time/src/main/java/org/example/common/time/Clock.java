package org.example.common.time;

import java.time.Instant;
import java.time.LocalDateTime;

public interface Clock {

    long nowMs();

    /** Returns a monotonic time source for measuring elapsed duration, not wall-clock time. */
    long monotonicTimeNanos();

    Instant now();

    LocalDateTime nowLocalDateTime();
}
