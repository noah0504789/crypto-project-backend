package org.example.common.exception;

public class DlqNotFoundException extends ResourceNotFoundException {
    public DlqNotFoundException(String id) {
        super("dlq not found. dlqId=" + id);
    }
}
