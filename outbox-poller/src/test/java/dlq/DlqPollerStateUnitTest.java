package dlq;

import org.example.outboxpoller.dlq.DlqPollerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DlqPollerStateUnitTest {

    @Test
    @DisplayName("DLQ poller는 비활성 상태로 생성된다")
    void constructor_shouldDisablePoller() {
        DlqPollerState state = new DlqPollerState();

        assertThat(state.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("start와 stop으로 DLQ poller 상태를 변경한다")
    void startAndStop_shouldChangeState() {
        DlqPollerState state = new DlqPollerState();

        state.start();
        assertThat(state.isEnabled()).isTrue();

        state.stop();
        assertThat(state.isEnabled()).isFalse();
    }
}
