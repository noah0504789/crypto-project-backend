package org.example.common.redis.lock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DistributedLockPolicy {

    CACHE_WARM_UP(100L, 3_000L, 3, 30L);

    private final long waitTimeMs;
    private final long leaseTimeMs;
    private final int retryAttempts;
    private final long retryDelayMs;
}
