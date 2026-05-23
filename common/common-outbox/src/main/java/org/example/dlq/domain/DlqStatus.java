package org.example.dlq.domain;

public enum DlqStatus {
    PENDING, PUBLISHED, PUBLISH_FAILED, COMPLETED, FAILED
}
