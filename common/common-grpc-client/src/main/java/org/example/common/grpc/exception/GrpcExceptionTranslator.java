package org.example.common.grpc.exception;

import io.grpc.Status;

public final class GrpcExceptionTranslator {

    private GrpcExceptionTranslator() {
    }

    public static GrpcClientException translate(Throwable t) {
        Status.Code grpcCode = Status.fromThrowable(t).getCode();

        GrpcFailureCode code = switch (grpcCode) {
            case DEADLINE_EXCEEDED -> GrpcFailureCode.DEADLINE_EXCEEDED;
            case CANCELLED -> GrpcFailureCode.CANCELLED;
            default -> GrpcFailureCode.UNKNOWN;
        };

        return new GrpcClientException(code, code.name(), t);
    }
}
