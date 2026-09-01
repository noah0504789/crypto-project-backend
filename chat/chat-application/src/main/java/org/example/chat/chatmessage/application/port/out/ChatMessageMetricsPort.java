package org.example.chat.chatmessage.application.port.out;

public interface ChatMessageMetricsPort {

    void recordHandler(Runnable action);

    void recordMessageInsert(Runnable action);

    void recordRoomCounter(Runnable action);

    void recordMembership(Runnable action);

    void recordCommittedBatch(int messageCount, int roomCount, int membershipDocumentCount);

    void recordDuplicateMessage();

    void recordRetryableFailure();

    void recordDlqPublished();

    void recordDlqPublishFailed();
}
