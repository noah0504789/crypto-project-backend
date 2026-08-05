package dlq;

import org.example.outboxpoller.dlq.DlqPollerProperties;
import org.example.outboxpoller.dlq.DlqPollerState;
import org.example.outboxpoller.dlq.DlqPollerStateInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DlqPollerStateInitializerUnitTest {

    @Test
    @DisplayName("enabled 설정이 false이면 DLQ poller를 비활성화한다")
    void init_whenDisabled_stopsPoller() {
        DlqPollerState state = new DlqPollerState();
        state.start();
        DlqPollerStateInitializer initializer = new DlqPollerStateInitializer(
                state,
                properties(false)
        );

        initializer.init();

        assertThat(state.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("enabled 설정이 true이면 DLQ poller를 활성화한다")
    void init_whenEnabled_startsPoller() {
        DlqPollerState state = new DlqPollerState();
        DlqPollerStateInitializer initializer = new DlqPollerStateInitializer(
                state,
                properties(true)
        );

        initializer.init();

        assertThat(state.isEnabled()).isTrue();
    }

    private DlqPollerProperties properties(boolean enabled) {
        return new DlqPollerProperties(enabled, 10000, 100);
    }
}
