package org.example.chat.chatmessage.application.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatMessageInsertStage {
    MESSAGE_INSERT("message_insert"),
    ROOM_COUNTER("room_counter"),
    MEMBERSHIP("membership");

    private final String stageTagValue;
}
