package org.example.common.inbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.jpa.BaseEntity;
import org.springframework.data.domain.Persistable;

@Entity
@Table(
        name = "inbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inbox_consumer_event",
                columnNames = {"consumer_name", "event_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inbox extends BaseEntity implements Persistable<String> {

    @Id
    @Column(length = 191)
    private String id;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Transient
    private boolean isNew = true;

    private Inbox(String consumerName, String eventId) {
        this.id = consumerName + ":" + eventId;
        this.consumerName = consumerName;
        this.eventId = eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    private void markNotNew() {
        isNew = false;
    }

    public static Inbox of(String consumerName, String eventId) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }

        return new Inbox(consumerName, eventId);
    }
}
