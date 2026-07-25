package org.example.common.dlq.domain;

public enum DlqStatus {
    PENDING, PUBLISHED, PUBLISH_FAILED, COMPLETED, CONSUME_FAILED
}
