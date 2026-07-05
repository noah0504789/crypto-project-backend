package org.example.chat.chatroom.application.exception;

import lombok.Getter;
import org.example.common.exception.InfrastructureException;

@Getter
public class ChatRoomEventPublishException extends InfrastructureException {

    private final String roomId;
    private final String context;

    public ChatRoomEventPublishException(
            String roomId,
            String context,
            Throwable cause
    ) {
        super(
                "failed to publish chatroom event. context=" + context + ", roomId=" + roomId,
                cause
        );
        this.roomId = roomId;
        this.context = context;
    }
}