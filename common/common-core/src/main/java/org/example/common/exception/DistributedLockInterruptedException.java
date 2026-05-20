package org.example.common.exception;

public class DistributedLockInterruptedException extends RuntimeException {

    public DistributedLockInterruptedException(String key, Throwable cause) {
        super("interrupted while acquiring distributed lock. key=" + key, cause);
    }
}