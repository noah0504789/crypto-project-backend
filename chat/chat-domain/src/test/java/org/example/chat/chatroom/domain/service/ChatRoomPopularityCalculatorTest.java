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

    @Test
    @DisplayName("메시지 1건의 증분(messageDelta)은 msgCnt 1 증가분(calculate 차이)과 같다")
    void messageDelta_shouldEqualCalculateDifferenceOfOneMessage() {
        // given
        ChatRoom before = ChatRoom.builder().msgCnt(5L).build();
        ChatRoom after = ChatRoom.builder().msgCnt(6L).build();

        // when
        double delta = ChatRoomPopularityCalculator.messageDelta();

        // then
        assertThat(delta).isEqualTo(1.0);
        assertThat(delta)
                .isEqualTo(ChatRoomPopularityCalculator.calculate(after)
                        - ChatRoomPopularityCalculator.calculate(before));
    }
}
