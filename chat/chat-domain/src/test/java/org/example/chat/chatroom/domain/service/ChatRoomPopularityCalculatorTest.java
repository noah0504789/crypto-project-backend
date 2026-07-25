package org.example.chat.chatroom.domain.service;

import org.example.chat.chatroom.domain.model.ChatRoom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomPopularityCalculatorTest {

    @Test
    @DisplayName("msgCnt를 popularity 점수로 변환한다")
    void calculate_shouldReturnMsgCntAsScore() {
        // given
        ChatRoom room = ChatRoom.builder().msgCnt(15L).build();

        // when
        double score = ChatRoomPopularityCalculator.calculate(room);

        // then
        assertThat(score).isEqualTo(15.0);
    }

    @Test
    @DisplayName("msgCnt가 0이면 popularity는 0이다")
    void calculate_shouldReturnZero_whenMsgCntIsZero() {
        // given
        ChatRoom room = ChatRoom.builder().msgCnt(0L).build();

        // when
        double score = ChatRoomPopularityCalculator.calculate(room);

        // then
        assertThat(score).isZero();
    }

    @Test
    @DisplayName("msgCnt가 null이면 popularity는 0이다")
    void calculate_shouldReturnZero_whenMsgCntIsNull() {
        // given
        ChatRoom room = ChatRoom.builder().msgCnt(null).build();

        // when
        double score = ChatRoomPopularityCalculator.calculate(room);

        // then
        assertThat(score).isZero();
    }

    @Test
    @DisplayName("ChatRoom이 null이면 popularity는 0이다")
    void calculate_shouldReturnZero_whenRoomIsNull() {
        // when
        double score = ChatRoomPopularityCalculator.calculate(null);

        // then
        assertThat(score).isZero();
    }

}
