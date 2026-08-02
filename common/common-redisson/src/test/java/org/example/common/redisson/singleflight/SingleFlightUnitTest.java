package org.example.common.redisson.singleflight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleFlightUnitTest {

    private final SingleFlight sut = new SingleFlight();

    @Test
    @DisplayName("먼저 로드 중인 key로 들어온 호출은 loader를 재실행하지 않고 결과를 공유한다")
    void sharesResultWhileLoadInFlight() throws Exception {
        AtomicInteger ownerCalls = new AtomicInteger();
        AtomicInteger followerCalls = new AtomicInteger();
        CountDownLatch ownerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // owner: inFlight에 등록 후 release까지 로더 안에서 대기
            Future<String> owner = pool.submit(() -> sut.execute("k", () -> {
                ownerCalls.incrementAndGet();
                ownerEntered.countDown();
                await(release);
                return "owner";
            }));

            await(ownerEntered);

            // follower: owner가 로드 중인 동안 같은 key로 진입 → join(공유)
            Future<String> follower = pool.submit(() -> sut.execute("k", () -> {
                followerCalls.incrementAndGet();
                return "follower";
            }));

            // follower가 execute에 진입해 join에 걸릴 시간을 준 뒤 owner 로더를 풀어준다.
            Thread.sleep(100);
            release.countDown();

            assertThat(owner.get()).isEqualTo("owner");
            assertThat(follower.get()).isEqualTo("owner"); // 공유된 결과
            assertThat(ownerCalls.get()).isEqualTo(1);
            assertThat(followerCalls.get()).isEqualTo(0); // 재실행 안 함
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("로드 완료 후 같은 key는 loader를 다시 실행한다")
    void reexecutesAfterCompletion() {
        AtomicInteger calls = new AtomicInteger();

        sut.execute("k", calls::incrementAndGet);
        sut.execute("k", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("loader 예외는 호출자에게 그대로 전파되고 key는 정리된다")
    void propagatesExceptionAndClearsKey() {
        assertThatThrownBy(() -> sut.execute("k", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        // 예외 후에도 같은 key로 정상 실행 가능(정리됨).
        assertThat(sut.execute("k", () -> "ok")).isEqualTo("ok");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
