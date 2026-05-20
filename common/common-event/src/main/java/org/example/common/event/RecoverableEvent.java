package org.example.common.event;

public interface RecoverableEvent<T> {

    void handle(T handler);
}
