package org.example.chat.chatroom.application.port.out;

import org.example.chat.chatroom.application.service.result.ChatRoomMemberReadState;
import org.example.chat.chatroom.application.service.result.MyChatRoomState;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChatRoomPersistencePort {

    Optional<ChatRoom> findById(String id);

    Optional<ChatRoom> findByIdWithLatestMessage(String id);

    List<ChatRoom> listPopularRooms(ChatRoomCategory category, int limit);

    List<ChatRoom> listPopularRoomsAfter(
            ChatRoomCategory category,
            String lastRoomId,
            Long lastPopularity,
            int limit
    );

    List<ChatRoom> listRoomsForPopularityRecompute(ChatRoomCategory category);

    void updatePopularities(Map<String, Long> roomIdToPopularity);

    /**
     * 내 방 목록 projection 을 다시 만들 때 쓰는 durable source. 사용자의 membership 전체와 방을
     * 함께 읽어 오며 정렬은 하지 않는다 — 정렬 점수 계산과 복구 상한 적용은 application 이
     * 담당한다(→ {@code MyChatRoomScoreCalculator}).
     */
    List<MyChatRoomState> listMyRoomStates(String memberId);

    Long getLastReadSeq(String id, String memberId);

    List<ChatRoomMemberReadState> listMemberReadStates(String id);

    boolean existsByTitle(String title);


    ChatRoom save(ChatRoom chatRoom);

    ChatRoom updateRoomAndReturn(String id, Map<String, Object> updates);

    long updateMessageState(String id, int count, long lastMessageCreatedAtMs);

    void decrementMessageCount(String id);

    void activateMembership(String id, String memberId, Long lastMsgReadSeq);

    void joinMembership(String id, String memberId);

    void leaveMembership(String id, String memberId);

    void deleteById(String id);
}
