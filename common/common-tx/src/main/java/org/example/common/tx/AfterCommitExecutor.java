package org.example.common.tx;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 현재 트랜잭션이 커밋된 뒤에 작업을 실행한다. 활성 트랜잭션이 없으면 즉시 실행한다(폴백).
 *
 * <p>Spring 트랜잭션 동기화 기반이라 JPA·Mongo 등 트랜잭션 매니저 종류와 무관하게 동작한다.
 * 캐시 반영처럼 "커밋된 것만 외부에 노출"해야 하는 후처리를 커밋 후로 미뤄, 커밋 전 실행 후 롤백되면
 * DB와 캐시가 어긋나는 유령/불일치를 막는 데 쓴다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AfterCommitExecutor {

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
