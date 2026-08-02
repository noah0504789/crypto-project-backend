package tx;

import org.example.common.tx.AfterCommitExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitExecutorUnitTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("활성 트랜잭션이 없으면 즉시 실행한다")
    void runsImmediately_whenNoActiveTransaction() {
        AtomicInteger count = new AtomicInteger();

        AfterCommitExecutor.run(count::incrementAndGet);

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 트랜잭션이 있으면 즉시 실행하지 않고 afterCommit 시점에 실행한다")
    void defersUntilAfterCommit_whenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger count = new AtomicInteger();

        AfterCommitExecutor.run(count::incrementAndGet);

        // 아직 커밋 전 → 실행 안 됨
        assertThat(count.get()).isZero();

        // 등록된 동기화의 afterCommit 을 호출하면 실행된다
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(count.get()).isEqualTo(1);
    }
}
