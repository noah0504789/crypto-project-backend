package id;

import org.example.common.id.SnowflakeIdGenerator;
import org.example.common.id.properties.IdGenProperties;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    private static final long SEQ_BITS = 12;
    private static final long WK_BITS = 5;
    private static final long DC_BITS = 5;

    private static final long WK_SHIFT = SEQ_BITS;
    private static final long DC_SHIFT = SEQ_BITS + WK_BITS;
    private static final long TIME_SHIFT = SEQ_BITS + WK_BITS + DC_BITS;

    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;
    private static final long MAX_WK = (1L << WK_BITS) - 1;
    private static final long MAX_DC = (1L << DC_BITS) - 1;

    SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

    @Test
    @DisplayName("currentValue가 있으면 기존 값을 그대로 반환한다")
    void generate_returnsCurrentValueWhenCurrentValueExists() {
        // given
        Long currentValue = 123L;

        // when
        Object result = generator.generate(
                null,
                new Object(),
                currentValue,
                EventType.INSERT
        );

        // then
        assertThat(result).isEqualTo(currentValue);
    }

    @Test
    @DisplayName("currentValue가 없으면 Snowflake ID를 생성한다")
    void generate_createsSnowflakeIdWhenCurrentValueIsNull() {
        // given
        int datacenterId = 3;
        int workerId = 7;
        Instant epoch = Instant.now().minusSeconds(60);

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        SharedSessionContractImplementor session = mockSession(epoch, datacenterId, workerId);

        // when
        Long id = (Long) generator.generate(
                session,
                new Object(),
                null,
                EventType.INSERT
        );

        // then
        assertThat(id).isNotNull();
        assertThat(id).isPositive();

        assertThat(extractDatacenterId(id)).isEqualTo(datacenterId);
        assertThat(extractWorkerId(id)).isEqualTo(workerId);
        assertThat(extractSequence(id)).isBetween(0L, MAX_SEQ);
        assertThat(extractTimestampPart(id)).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("같은 generator에서 여러 ID를 생성해도 중복되지 않는다")
    void generate_createsUniqueIds() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        SharedSessionContractImplementor session = mockSession(
                Instant.now().minusSeconds(60),
                1,
                2
        );

        Set<Long> ids = new HashSet<>();

        // when
        for (int i = 0; i < 1_000; i++) {
            Long id = (Long) generator.generate(
                    session,
                    new Object(),
                    null,
                    EventType.INSERT
            );

            ids.add(id);
        }

        // then
        assertThat(ids).hasSize(1_000);
    }

    @Test
    @DisplayName("생성된 ID는 대체로 증가한다")
    void generate_createsIncreasingIds() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        SharedSessionContractImplementor session = mockSession(
                Instant.now().minusSeconds(60),
                1,
                1
        );

        // when
        Long first = (Long) generator.generate(session, new Object(), null, EventType.INSERT);
        Long second = (Long) generator.generate(session, new Object(), null, EventType.INSERT);
        Long third = (Long) generator.generate(session, new Object(), null, EventType.INSERT);

        // then
        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
    }

    @Test
    @DisplayName("datacenterId가 허용 범위를 넘으면 예외가 발생한다")
    void generate_throwsExceptionWhenDatacenterIdIsOutOfRange() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        SharedSessionContractImplementor session = mockSession(
                Instant.now().minusSeconds(60),
                32,
                1
        );

        // when & then
        assertThatThrownBy(() -> generator.generate(session, new Object(), null, EventType.INSERT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dcId out of range");
    }

    @Test
    @DisplayName("workerId가 허용 범위를 넘으면 예외가 발생한다")
    void generate_throwsExceptionWhenWorkerIdIsOutOfRange() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        SharedSessionContractImplementor session = mockSession(
                Instant.now().minusSeconds(60),
                1,
                32
        );

        // when & then
        assertThatThrownBy(() -> generator.generate(session, new Object(), null, EventType.INSERT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId out of range");
    }

    @Test
    @DisplayName("epoch가 미래이면 예외가 발생한다")
    void generate_throwsExceptionWhenEpochIsFuture() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        SharedSessionContractImplementor session = mockSession(
                Instant.now().plusSeconds(60),
                1,
                1
        );

        // when & then
        assertThatThrownBy(() -> generator.generate(session, new Object(), null, EventType.INSERT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epoch must not be in the future");
    }

    @Test
    @DisplayName("ManagedBeanRegistry가 없으면 예외가 발생한다")
    void generate_throwsExceptionWhenManagedBeanRegistryIsMissing() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        ServiceRegistryImplementor serviceRegistry = proxy(ServiceRegistryImplementor.class, (proxy, method, args) -> {
            if ("getService".equals(method.getName()) && args[0] == ManagedBeanRegistry.class) {
                return null;
            }

            return defaultValue(method);
        });

        SessionFactoryImplementor factory = proxy(SessionFactoryImplementor.class, (proxy, method, args) -> {
            if ("getServiceRegistry".equals(method.getName())) {
                return serviceRegistry;
            }

            return defaultValue(method);
        });

        SharedSessionContractImplementor session = proxy(SharedSessionContractImplementor.class, (proxy, method, args) -> {
            if ("getFactory".equals(method.getName())) {
                return factory;
            }

            return defaultValue(method);
        });

        // when & then
        assertThatThrownBy(() -> generator.generate(session, new Object(), null, EventType.INSERT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ManagedBeanRegistry not available");
    }

    @Test
    @DisplayName("지원 이벤트 타입은 INSERT다")
    void getEventTypes_returnsInsertOnly() {
        // given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        // when & then
        assertThat(generator.getEventTypes())
                .containsExactly(EventType.INSERT);
    }

    private SharedSessionContractImplementor mockSession(
            Instant epoch,
            int datacenterId,
            int workerId
    ) {
        IdGenProperties properties = new IdGenProperties(
                epoch,
                datacenterId,
                workerId
        );

        @SuppressWarnings("unchecked")
        ManagedBean<IdGenProperties> managedBean = proxy(ManagedBean.class, (proxy, method, args) -> {
            if ("getBeanInstance".equals(method.getName())) {
                return properties;
            }

            return defaultValue(method);
        });

        ManagedBeanRegistry registry = proxy(ManagedBeanRegistry.class, (proxy, method, args) -> {
            if ("getBean".equals(method.getName()) && args[0] == IdGenProperties.class) {
                return managedBean;
            }

            return defaultValue(method);
        });

        ServiceRegistryImplementor serviceRegistry = proxy(ServiceRegistryImplementor.class, (proxy, method, args) -> {
            if ("getService".equals(method.getName()) && args[0] == ManagedBeanRegistry.class) {
                return registry;
            }

            return defaultValue(method);
        });

        SessionFactoryImplementor factory = proxy(SessionFactoryImplementor.class, (proxy, method, args) -> {
            if ("getServiceRegistry".equals(method.getName())) {
                return serviceRegistry;
            }

            return defaultValue(method);
        });

        SharedSessionContractImplementor session = proxy(SharedSessionContractImplementor.class, (proxy, method, args) -> {
            if ("getFactory".equals(method.getName())) {
                return factory;
            }

            return defaultValue(method);
        });

        return session;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        Object target = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Object objectMethodValue = objectMethodValue(proxy, method, args);

                    if (objectMethodValue != null) {
                        return objectMethodValue;
                    }

                    return handler.invoke(proxy, method, args);
                }
        );

        return type.cast(target);
    }

    private static Object objectMethodValue(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();

        if (!returnType.isPrimitive()) {
            return null;
        }

        if (returnType == boolean.class) {
            return false;
        }

        if (returnType == void.class) {
            return null;
        }

        if (returnType == char.class) {
            return '\0';
        }

        return 0;
    }

    private long extractTimestampPart(long id) {
        return id >>> TIME_SHIFT;
    }

    private long extractDatacenterId(long id) {
        return (id >>> DC_SHIFT) & MAX_DC;
    }

    private long extractWorkerId(long id) {
        return (id >>> WK_SHIFT) & MAX_WK;
    }

    private long extractSequence(long id) {
        return id & MAX_SEQ;
    }
}
