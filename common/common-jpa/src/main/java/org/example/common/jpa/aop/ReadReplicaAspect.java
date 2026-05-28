package org.example.common.jpa.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.common.jpa.datasource.DataSourceContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(1)
public class ReadReplicaAspect {

    @Around("@annotation(org.example.common.jpa.annotation.ReadReplica) || @within(org.example.common.jpa.annotation.ReadReplica)")
    public Object routeToReadReplica(ProceedingJoinPoint joinPoint) throws Throwable {
        if (isWriteTransactionActive()) {
            return joinPoint.proceed();
        }

        try (DataSourceContextHolder.ReadScope ignored = DataSourceContextHolder.read()) {
            return joinPoint.proceed();
        }
    }

    private boolean isWriteTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive() && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}