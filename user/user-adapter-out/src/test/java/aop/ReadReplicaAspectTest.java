package aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.example.common.jpa.aop.ReadReplicaAspect;
import org.example.common.jpa.datasource.DataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReadReplicaAspect 단위 테스트")
class ReadReplicaAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private final ReadReplicaAspect sut = new ReadReplicaAspect();

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    @DisplayName("@ReadReplica 메서드는 READ context가 켜진 상태로 실행된다")
    void routeToReadReplica() throws Throwable {
        // given
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(DataSourceContextHolder.isRead()).isTrue();
            return "result";
        });

        // when
        Object result = sut.routeToReadReplica(joinPoint);

        // then
        assertThat(result).isEqualTo("result");
        assertThat(DataSourceContextHolder.isRead()).isFalse();

        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("@ReadReplica 메서드에서 예외가 발생해도 READ context는 정리된다")
    void clearReadContextWhenProceedThrowsException() throws Throwable {
        // given
        RuntimeException exception = new RuntimeException("boom");

        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(DataSourceContextHolder.isRead()).isTrue();
            throw exception;
        });

        // when & then
        assertThatThrownBy(() -> sut.routeToReadReplica(joinPoint))
                .isSameAs(exception);

        assertThat(DataSourceContextHolder.isRead()).isFalse();

        verify(joinPoint).proceed();
    }
}