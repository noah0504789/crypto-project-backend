package org.example.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.infra.mysql.DataSourceContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(-1)
public class ReadReplicaAspect {

    @Around("@annotation(org.example.infra.annotation.ReadReplica) || @within(org.example.infra.annotation.ReadReplica)")
    public Object routeToReadReplica(ProceedingJoinPoint joinPoint) throws Throwable {
        try (DataSourceContextHolder.ReadScope ignored = DataSourceContextHolder.read()) {
            return joinPoint.proceed();
        }
    }
}