package org.example.common.inbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.jpa.BaseEntity;

@Entity
@Table(
        name = "inbox_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inbox_event_consumer_event",
                columnNames = {"consumer_name", "event_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent extends BaseEntity {

    @Id
    @Column(length = 191)
    private String id;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    private InboxEvent(String consumerName, String eventId) {
        this.id = consumerName + ":" + eventId;
        this.consumerName = consumerName;
        this.eventId = eventId;
    }

    public static InboxEvent of(String consumerName, String eventId) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }

        return new InboxEvent(consumerName, eventId);
    }
}
