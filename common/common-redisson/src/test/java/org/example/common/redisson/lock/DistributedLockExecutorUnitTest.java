package org.example.common.redisson.lock;

import org.example.common.exception.DistributedLockAcquireFailedException;
import org.example.common.exception.DistributedLockInterruptedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockExecutorUnitTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private DistributedLockExecutor executor;

    private final String lockKey = "chatroom:findById:1";
    private final DistributedLockPolicy lockPolicy = DistributedLockPolicy.CACHE_WARM_UP;

    @BeforeEach
    void setUp() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @Test
    void should_execute_supplier_and_unlock_when_lock_acquired() throws Exception {
        // given
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(rLock.isHeldByCurrentThread())
                .thenReturn(true);

        Supplier<String> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn("success");

        // when
        String result = executor.execute(
                lockKey,
                supplier,
                lockPolicy
        );

        // then
        assertThat(result).isEqualTo("success");

        verify(redissonClient).getLock(anyString());
        verify(rLock).tryLock(
                lockPolicy.getWaitTimeMs(),
                lockPolicy.getLeaseTimeMs(),
                TimeUnit.MILLISECONDS
        );
        verify(supplier, times(1)).get();
        verify(rLock, times(1)).unlock();
    }

    @Test
    void should_retry_and_execute_supplier_when_lock_acquired_after_failure() throws Exception {
        // given
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(false)
                .thenReturn(true);
        when(rLock.isHeldByCurrentThread())
                .thenReturn(true);

        Supplier<String> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn("success");

        // when
        String result = executor.execute(
                lockKey,
                supplier,
                lockPolicy
        );

        // then
        assertThat(result).isEqualTo("success");

        verify(rLock, times(2)).tryLock(
                lockPolicy.getWaitTimeMs(),
                lockPolicy.getLeaseTimeMs(),
                TimeUnit.MILLISECONDS
        );
        verify(supplier, times(1)).get();
        verify(rLock, times(1)).unlock();
    }

    @Test
    void should_throw_exception_and_not_execute_supplier_when_lock_acquire_fails_after_retries() throws Exception {
        // given
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(false);

        Supplier<String> supplier = mock(Supplier.class);

        // when & then
        assertThatThrownBy(() -> executor.execute(
                lockKey,
                supplier,
                lockPolicy
        )).isInstanceOf(DistributedLockAcquireFailedException.class);

        int maxAttempts = lockPolicy.getRetryAttempts() + 1;

        verify(rLock, times(maxAttempts)).tryLock(
                lockPolicy.getWaitTimeMs(),
                lockPolicy.getLeaseTimeMs(),
                TimeUnit.MILLISECONDS
        );
        verify(supplier, never()).get();
        verify(rLock, never()).unlock();
    }

    @Test
    void should_unlock_and_propagate_exception_when_supplier_throws() throws Exception {
        // given
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(rLock.isHeldByCurrentThread())
                .thenReturn(true);

        RuntimeException expected = new RuntimeException("supplier failed");

        Supplier<String> supplier = mock(Supplier.class);
        when(supplier.get()).thenThrow(expected);

        // when & then
        assertThatThrownBy(() -> executor.execute(
                lockKey,
                supplier,
                lockPolicy
        )).isSameAs(expected);

        verify(rLock, times(1)).tryLock(
                lockPolicy.getWaitTimeMs(),
                lockPolicy.getLeaseTimeMs(),
                TimeUnit.MILLISECONDS
        );
        verify(supplier, times(1)).get();
        verify(rLock, times(1)).unlock();
    }

    @Test
    void should_restore_interrupt_flag_and_throw_exception_when_interrupted_during_lock_acquire() throws Exception {
        // given
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException());

        Supplier<String> supplier = mock(Supplier.class);

        // when & then
        assertThatThrownBy(() -> executor.execute(
                lockKey,
                supplier,
                lockPolicy
        )).isInstanceOf(DistributedLockInterruptedException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        Thread.interrupted();

        verify(supplier, never()).get();
        verify(rLock, never()).unlock();
    }

    @Test
    void should_ignore_IllegalMonitorStateException_when_unlockAlreadyReleased() throws Exception {
        // given
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(rLock.isHeldByCurrentThread())
                .thenReturn(true);
        doThrow(new IllegalMonitorStateException())
                .when(rLock)
                .unlock();

        Supplier<String> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn("success");

        // when
        String result = executor.execute(
                lockKey,
                supplier,
                lockPolicy
        );

        // then
        assertThat(result).isEqualTo("success");
        verify(supplier, times(1)).get();
        verify(rLock, times(1)).unlock();
    }

    @Test
    void should_not_unlock_when_currentThread_doesNotHoldLock() throws Exception {
        // given
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(rLock.isHeldByCurrentThread())
                .thenReturn(false);

        Supplier<String> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn("success");

        // when
        String result = executor.execute(
                lockKey,
                supplier,
                lockPolicy
        );

        // then
        assertThat(result).isEqualTo("success");
        verify(supplier, times(1)).get();
        verify(rLock, never()).unlock();
    }
}