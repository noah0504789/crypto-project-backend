package org.example.chat.chatroom.application.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 인기방 zset 재구축 시 담는 상위 방 개수. 조회 페이지 크기보다 충분히 커야
 * 커서 페이지네이션이 인덱스 밖으로 나가지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "chat.popular-room")
public record PopularChatRoomProperties(@Positive Integer indexSize) {
}
