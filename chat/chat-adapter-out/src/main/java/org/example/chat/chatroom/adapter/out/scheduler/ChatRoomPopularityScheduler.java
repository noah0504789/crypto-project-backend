package org.example.chat.chatroom.adapter.out.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.port.in.PopularChatRoomRefreshUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인기방 zset을 3시간마다 재계산한다. 실제 재구축 로직은 {@link PopularChatRoomRefreshUseCase}가 담당하고,
 * 이 컴포넌트는 트리거만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomPopularityScheduler {

    private final PopularChatRoomRefreshUseCase popularChatRoomRefreshUseCase;

    @Scheduled(cron = "0 0 */3 * * *")
    public void refreshPopularRooms() {
        log.info("[popularity] scheduled rebuild start");
        popularChatRoomRefreshUseCase.refresh();
        log.info("[popularity] scheduled rebuild done");
    }
}
