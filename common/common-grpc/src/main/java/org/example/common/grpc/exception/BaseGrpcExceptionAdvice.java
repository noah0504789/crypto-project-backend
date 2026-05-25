package org.example.common.grpc.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.example.common.exception.InfrastructureException;
import org.example.common.exception.InvalidRequestException;
import org.example.common.exception.ResourceNotFoundException;

@Slf4j
public abstract class BaseGrpcExceptionAdvice {

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public StatusRuntimeException handleResourceNotFound(ResourceNotFoundException e) {
        log.warn("grpc NOT_FOUND: {}", e.getMessage());
        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(InvalidRequestException.class)
    public StatusRuntimeException handleInvalidRequest(InvalidRequestException e) {
        log.warn("grpc INVALID_ARGUMENT: {}", e.getMessage());
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public StatusRuntimeException handleIllegalArgument(IllegalArgumentException e) {
        log.warn("grpc INVALID_ARGUMENT: {}", e.getMessage());
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(IllegalStateException.class)
    public StatusRuntimeException handleIllegalState(IllegalStateException e) {
        log.warn("grpc FAILED_PRECONDITION: {}", e.getMessage());
        return Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(InfrastructureException.class)
    public StatusRuntimeException handleInfrastructure(InfrastructureException e) {
        log.error("grpc INTERNAL(infra): {}", e.getMessage(), e);
        return Status.INTERNAL.withDescription("Internal service error").asRuntimeException();
    }

    @GrpcExceptionHandler(StatusRuntimeException.class)
    public StatusRuntimeException handleStatusRuntimeException(StatusRuntimeException e) {
        log.warn("grpc status exception: status={}, description={}", e.getStatus(), e.getStatus().getDescription());
        return e;
    }

    @GrpcExceptionHandler(Exception.class)
    public StatusRuntimeException handleUnknown(Exception e) {
        log.error("grpc INTERNAL(unexpected): {}", e.getMessage(), e);
        return Status.INTERNAL.withDescription("unexpected grpc server error").asRuntimeException();
    }
}
