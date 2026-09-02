package org.example.chat.chatroom.application.enums;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatRoomActivityProjectionMetricNames {

    public static final String FLUSH_DURATION = "chat.room.activity.projection.flush";
    public static final String ROOMS = "chat.room.activity.projection.rooms";
    public static final String MEMBERS = "chat.room.activity.projection.members";
    public static final String SCORE_MISMATCHES = "chat.room.activity.projection.score.mismatches";
    public static final String DIRTY_BACKLOG = "chat.room.activity.projection.dirty.backlog";
}
