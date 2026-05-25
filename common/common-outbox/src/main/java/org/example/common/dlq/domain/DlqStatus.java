package org.example.common.dlq.domain;

public enum DlqStatus {
    PENDING, PUBLISHED, PUBLISH_FAILED, COMPLETED, COMSUME_FAILED
}
