package org.example.websocket.gateway.chatmessage.application.port.out;

import org.example.common.grpc.exception.GrpcFailureCode;

public interface ChatMessageMetricsPort {

    void recordSaveFailure(GrpcFailureCode code);

    void recordHardDeleteFailure(GrpcFailureCode code);
}