package org.example.chat.chatroom.application.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 방 activity projector 의 주기와 상한. flush 간격이 곧 "내 방 목록이 최신으로 올라오기까지"
 * 허용하는 eventual consistency 창이다.
 *
 * <p>{@code claimTimeoutMs} 는 claim 한 방을 처리하지 못한 채 죽었다고 보는 기준이며,
 * 이 시간이 지난 inflight 항목은 재생성 경로로 회수된다.
 */
@Validated
@ConfigurationProperties(prefix = "chat.room-activity-projection")
public record ChatRoomActivityProjectionProperties(
        @Positive Integer claimBatchSize,
        @Positive Long claimTimeoutMs,
        @Positive Integer reclaimBatchSize
) {
}
