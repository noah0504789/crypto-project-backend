package org.example.common.grpc.exception;

import lombok.Getter;

@Getter
public class GrpcClientException extends RuntimeException {

    private final GrpcFailureCode code;

    public GrpcClientException(GrpcFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static GrpcClientException resolve(Throwable error) {
        Throwable cur = error;

        while (cur != null) {
            if (cur instanceof GrpcClientException e) {
                return e;
            }

            cur = cur.getCause();
        }

        return new GrpcClientException(
                GrpcFailureCode.UNKNOWN,
                GrpcFailureCode.UNKNOWN.name(),
                error
        );
    }
}
