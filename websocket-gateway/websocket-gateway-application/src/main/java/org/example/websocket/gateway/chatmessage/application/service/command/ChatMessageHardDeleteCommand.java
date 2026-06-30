package org.example.websocket.gateway.chatmessage.application.service.command;

public record ChatMessageHardDeleteCommand(
        String messageId,
        String roomId,
        String reason
) {

    private static final String SAVE_TIMEOUT_REASON = "save failed after timeout";

    public static ChatMessageHardDeleteCommand dueToSaveTimeout(ChatMessageSendCommand command) {
        return new ChatMessageHardDeleteCommand(
                command.messageId(),
                command.roomId(),
                SAVE_TIMEOUT_REASON
        );
    }
}