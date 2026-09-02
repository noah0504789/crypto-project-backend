package org.example.chat.chatroom.adapter.out.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.port.in.ChatRoomActivityProjectionUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * dirty 로 표시된 방의 정렬 projection 을 주기적으로 반영한다. 실제 계산은
 * {@link ChatRoomActivityProjectionUseCase} 가 하고 이 컴포넌트는 트리거만 한다.
 *
 * <p>{@code fixedDelay} 다. flush 가 창보다 오래 걸릴 때 밀린 실행이 연달아 터지지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomActivityProjectionScheduler {

    private final ChatRoomActivityProjectionUseCase chatRoomActivityProjectionUseCase;

    @Scheduled(fixedDelayString = "${chat.scheduler.room-activity-projection.flush-delay-ms}")
    public void flush() {
        chatRoomActivityProjectionUseCase.flush();
    }

    @Scheduled(fixedDelayString = "${chat.scheduler.room-activity-projection.reclaim-delay-ms}")
    public void reclaimStalled() {
        chatRoomActivityProjectionUseCase.reclaimStalled();
    }
}
