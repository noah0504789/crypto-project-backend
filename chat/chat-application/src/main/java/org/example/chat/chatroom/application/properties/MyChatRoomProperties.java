package org.example.chat.chatroom.application.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 내 방 정렬 projection 을 Mongo 로 재생성할 때 한 사용자에 대해 읽는 방 수 상한.
 * 정렬 키가 방 쪽 사실이라 Mongo 인덱스 하나로 정렬할 수 없어, 상한까지 읽어 application 이
 * 계산한다. 이 값을 넘는 방을 가진 사용자는 오래된 방이 목록에서 빠질 수 있다.
 */
@Validated
@ConfigurationProperties(prefix = "chat.my-room")
public record MyChatRoomProperties(@Positive Integer rebuildLimit) {
}
