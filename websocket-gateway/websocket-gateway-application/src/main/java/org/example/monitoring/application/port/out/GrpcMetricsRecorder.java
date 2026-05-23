package org.example.monitoring.application.port.out;

import io.grpc.Status;

public interface GrpcMetricsRecorder {
    void recordChatMessageSaveError(Status.Code code);

    void recordChatMessageHardDeleteError(Status.Code code);
}
