package org.example.common.clock;

import java.time.Instant;
import java.time.LocalDateTime;

public interface Clock {

    long nowMs();

    Instant now();

    LocalDateTime nowLocalDateTime();
}
