package org.example.common.redis.failover;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.*;


@Slf4j
@Aspect
@Component
public class CacheFailOpenAspect {

    @Around("@annotation(org.example.common.redis.failover.CacheFailOpen) || @within(org.example.common.redis.failover.CacheFailOpen)")
    public Object guard(ProceedingJoinPoint pjp) {
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            log.warn("[CACHE] fail-open on {}.{}",
                    pjp.getSignature().getDeclaringTypeName(),
                    pjp.getSignature().getName(),
                    e);

            return defaultFor(pjp);
        }
    }

    private Object defaultFor(ProceedingJoinPoint pjp) {
        Class<?> rt = ((MethodSignature) pjp.getSignature()).getReturnType();

        if (rt == void.class) return null;
        if (Optional.class.isAssignableFrom(rt)) return Optional.empty();
        if (List.class.isAssignableFrom(rt)) return Collections.emptyList();
        if (Set.class.isAssignableFrom(rt)) return Collections.emptySet();
        if (Map.class.isAssignableFrom(rt)) return Collections.emptyMap();
        if (Collection.class.isAssignableFrom(rt)) return Collections.emptyList();
        if (rt.isArray()) return Array.newInstance(rt.getComponentType(), 0);

        if (rt == boolean.class) return false;
        if (rt == byte.class) return (byte) 0;
        if (rt == short.class) return (short) 0;
        if (rt == int.class) return 0;
        if (rt == long.class) return 0L;
        if (rt == float.class) return 0f;
        if (rt == double.class) return 0d;
        if (rt == char.class) return '\0';

        // 레퍼런스 타입은 null → 서비스가 DB fallback 타도록
        return null;
    }
}
