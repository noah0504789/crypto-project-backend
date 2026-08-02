package org.example.notification.adapter.out.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.example.common.time.ServiceTimeConverter;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Notification master 캐시 표현. 조회에 필요한 불변 master 정보만 담는다.
 * recipient(읽음 상태)는 캐싱 대상이 아니다.
 */
@ToString
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RedisNotification {

    private String id;
    private NotificationType type;
    private String title;
    private String message;

    @JsonProperty("message_parts")
    private List<NotificationMessagePart> messageParts;

    private String link;
    private Map<String, Object> payload;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static RedisNotification fromDomain(Notification domain) {
        return RedisNotification.builder()
                .id(domain.getId())
                .type(domain.getType())
                .title(domain.getTitle())
                .message(domain.getMessage())
                .messageParts(domain.getMessageParts())
                .link(domain.getLink())
                .payload(domain.getPayload())
                .createdAt(ServiceTimeConverter.toInstant(domain.getCreatedAt()))
                .build();
    }

    /**
     * 캐시에는 삭제되지 않은 master 만 적재하므로 deleted=false 로 rehydrate 한다.
     */
    public Notification toDomain() {
        return Notification.rehydrate(
                id,
                type,
                title,
                message,
                messageParts,
                link,
                payload,
                false,
                null,
                ServiceTimeConverter.toLocalDateTime(createdAt)
        );
    }
}
