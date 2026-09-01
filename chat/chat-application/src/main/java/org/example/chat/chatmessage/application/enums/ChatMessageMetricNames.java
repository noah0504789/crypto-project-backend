package org.example.chat.chatmessage.application.enums;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatMessageMetricNames {

    public static final String HANDLER_DURATION = "chat.message.persistence.handler";
    public static final String STAGE_DURATION = "chat.message.persistence.stage";
    public static final String BATCH_MESSAGES = "chat.message.persistence.batch.messages";
    public static final String BATCH_ROOMS = "chat.message.persistence.batch.rooms";
    public static final String MEMBERSHIP_DOCUMENTS = "chat.message.persistence.membership.documents";
    public static final String MESSAGES = "chat.message.persistence.messages";
    public static final String RETRY_FAILURES = "chat.message.persistence.retry.failures";
    public static final String DLQ_TRANSITIONS = "chat.message.persistence.dlq.transitions";
}
