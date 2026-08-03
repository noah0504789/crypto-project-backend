package org.example.common.inbox.exception;

public class DuplicateInboxException extends InboxException {

    public DuplicateInboxException(String consumerName, String eventId, Throwable cause) {
        super(
                "Event already processed. consumerName=%s, eventId=%s"
                        .formatted(consumerName, eventId),
                cause
        );
    }
}
