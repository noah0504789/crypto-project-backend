package org.example.common.redisson.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.exception.DistributedLockAcquireFailedException;
import org.example.common.exception.DistributedLockInterruptedException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private static final String LOCK_PREFIX = "lock:";

    private final RedissonClient redissonClient;

    public <T> T execute(String key, Supplier<T> supplier, DistributedLockPolicy lockPolicy) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);

        DistributedLockAcquireFailedException lastException = null;
        int maxAttempts = lockPolicy.getRetryAttempts() + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DistributedLockResource lockResource;

            try {
                lockResource = acquire(lock, key, lockPolicy);
            } catch (DistributedLockAcquireFailedException e) {
                lastException = e;
                if (attempt >= maxAttempts) break;

                log.debug("[lock] acquire failed. retrying. key={}, attempt={}/{}", key, attempt, maxAttempts);

                sleep(lockPolicy.getRetryDelayMs(), key);
                continue;
            }

            try (lockResource) {
                return supplier.get();
            }
        }

        throw lastException;
    }

    private void sleep(long retryDelayMs, String key) {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockInterruptedException(key, e);
        }
    }

    private DistributedLockResource acquire(RLock lock, String key, DistributedLockPolicy lockPolicy) {
        try {
            boolean locked = lock.tryLock(lockPolicy.getWaitTimeMs(), lockPolicy.getLeaseTimeMs(), TimeUnit.MILLISECONDS);
            if (!locked) throw new DistributedLockAcquireFailedException(key);

            return new DistributedLockResource(lock, key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockInterruptedException(key, e);
        }
    }

    private record DistributedLockResource(RLock lock, String key) implements AutoCloseable {
        @Override
        public void close() {
            try {
                if (lock.isHeldByCurrentThread()) lock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.warn("[lock] already unlocked. key={}", key, e);
            }
        }
    }
}
