package org.example.chat.chatroom.application.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 내 방 정렬 projection 을 Mongo 로 재생성할 때 Redis 인덱스에 다시 넣는 방 수 상한.
 * 정렬 키가 방 쪽 사실이라 membership 전체와 방을 읽은 뒤 application 이 점수를 계산하고,
 * 그 결과의 상위 방에 이 상한을 적용한다.
 */
@Validated
@ConfigurationProperties(prefix = "chat.my-room")
public record MyChatRoomProperties(@Positive Integer rebuildLimit) {
}
