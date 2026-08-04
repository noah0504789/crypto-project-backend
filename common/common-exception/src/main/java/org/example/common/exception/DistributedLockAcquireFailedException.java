package org.example.common.exception;

public class DistributedLockAcquireFailedException extends InfrastructureException {

    public DistributedLockAcquireFailedException(String key) {
        super("failed to acquire distributed lock. key=" + key);
    }
}