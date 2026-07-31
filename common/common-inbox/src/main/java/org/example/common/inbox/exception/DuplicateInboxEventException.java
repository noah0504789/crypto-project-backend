package org.example.common.inbox.exception;

public class DuplicateInboxEventException extends InboxException {

    public DuplicateInboxEventException(String consumerName, String eventId, Throwable cause) {
        super(
                "Event already processed. consumerName=%s, eventId=%s"
                        .formatted(consumerName, eventId),
                cause
        );
    }
}
