package org.example.common.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum KafkaHeaderKey {

    TRANSACTION_ID("transaction_id"),
    TYPE_ID("__TypeId__"),
    DLQ_ID("dlq_id");

    private final String value;

    public String value() {
        return value;
    }
}
