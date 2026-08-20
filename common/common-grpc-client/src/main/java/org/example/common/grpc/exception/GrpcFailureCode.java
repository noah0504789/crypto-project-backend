package org.example.common.grpc.exception;

public enum GrpcFailureCode {

    DEADLINE_EXCEEDED,
    CANCELLED,
    UNKNOWN;

    public boolean isRecordable() {
        return this == DEADLINE_EXCEEDED || this == CANCELLED;
    }
}
