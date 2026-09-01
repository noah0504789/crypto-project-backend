package org.example.chat.chatroom.application.port.out;

public interface ChatRoomActivityProjectionMetricsPort {

    void recordFlush(Runnable action);

    void recordClaimedRooms(int rooms);

    void recordProjectedRoom(int updatedMembers, int mismatchedMembers);

    void recordRebuiltRoom(int members);

    void recordReclaimedRooms(int rooms);

    void recordDiscardedRoom();

    void recordFailedRoom();

    void recordDirtyBacklog(long dirtyRooms);
}
