package org.example.user.adapter.in.exception;

import net.devh.boot.grpc.server.advice.GrpcAdvice;
import org.example.grpc.common.exception.BaseGrpcExceptionAdvice;

@GrpcAdvice
public class GlobalGrpcExceptionAdvice extends BaseGrpcExceptionAdvice {
}
