package org.example.chat.chatmessage.adapter.out.cache;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDateTime;

@ToString
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RedisChatMessage {

    private String id;

    @JsonProperty("room_id")
    private String roomId;

    @JsonProperty("writer_id")
    private String writerId;
    private String content;

    @JsonProperty("created_at")
//    @JsonFormat(pattern = "yyyy/MM/dd HH:mm:ss.SSS", timezone = "UTC")
//    private LocalDateTime createdAt;
    private Instant createdAt;
}
