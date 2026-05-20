package org.example.common.event;

public interface HandleableEvent<T> {
    void handle(T handler, String txId);
}
