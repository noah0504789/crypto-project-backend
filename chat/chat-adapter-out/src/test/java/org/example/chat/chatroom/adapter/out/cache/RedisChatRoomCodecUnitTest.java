package org.example.chat.chatroom.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisChatRoomCodecUnitTest {

    private final RedisChatRoomCodec sut = new RedisChatRoomCodec(new ObjectMapper());

    @Test
    @DisplayName("기존 room hash에 watermark가 없으면 msgCnt를 초기값으로 읽는다")
    void readShouldFallbackToMessageCountWhenLastMsgSeqIsMissing() {
        // when
        RedisChatRoom room = sut.read(Map.of(
                "id", "room-id",
                "msg_cnt", "7"
        ));

        // then
        assertThat(room.getMsgCnt()).isEqualTo(7L);
        assertThat(room.getLastMsgSeq()).isEqualTo(7L);
    }

}
