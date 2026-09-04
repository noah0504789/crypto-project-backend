package org.example.chat.chatroom.adapter.in.web.dto;

import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomResponseUnitTest {

    @Test
    @DisplayName("보관 메시지 수와 읽음 위치 watermark를 별도 필드로 반환한다")
    void fromDomain_shouldExposeMessageCountAndLastMsgSeqSeparately() {
        // given
        ChatRoom room = ChatRoom.builder()
                .id("room-id")
                .hostId("host-id")
                .title("title")
                .description("description")
                .category(ChatRoomCategory.FREE)
                .memberIds(Set.of("host-id"))
                .msgCnt(5L)
                .lastMsgSeq(8L)
                .createdAt(LocalDateTime.now())
                .build();

        // when
        ChatRoomResponse response = ChatRoomResponse.fromDomain(room);

        // then
        assertThat(response.msgCnt()).isEqualTo(5L);
        assertThat(response.lastMsgSeq()).isEqualTo(8L);
    }
}
