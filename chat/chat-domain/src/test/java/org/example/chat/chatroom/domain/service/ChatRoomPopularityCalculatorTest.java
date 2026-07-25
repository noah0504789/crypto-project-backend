package org.example.chat.chatroom.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomPopularityCalculatorTest {

    @Test
    @DisplayName("msgCnt를 popularity 점수로 변환한다")
    void calculate_shouldReturnMsgCntAsScore() {
        // when
        double score = ChatRoomPopularityCalculator.calculate(15L);

        // then
        assertThat(score).isEqualTo(15.0);
    }

    @Test
    @DisplayName("msgCnt가 0이면 popularity는 0이다")
    void calculate_shouldReturnZero_whenMsgCntIsZero() {
        // when
        double score = ChatRoomPopularityCalculator.calculate(0L);

        // then
        assertThat(score).isZero();
    }

    @Test
    @DisplayName("msgCnt가 null이면 popularity는 0이다")
    void calculate_shouldReturnZero_whenMsgCntIsNull() {
        // when
        double score = ChatRoomPopularityCalculator.calculate(null);

        // then
        assertThat(score).isZero();
    }
}
