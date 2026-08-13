package time;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.common.time.ClockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockServiceUnitTest {

    @Test
    @DisplayName("단조 시간은 경과 시간 측정에 사용할 수 있도록 감소하지 않는다")
    void monotonicTimeNanos_calledSequentially_notDecrease() {
        // given
        ClockService sut = new ClockService();

        // when
        long first = sut.monotonicTimeNanos();
        long second = sut.monotonicTimeNanos();

        // then
        assertThat(second).isGreaterThanOrEqualTo(first);
    }
}
