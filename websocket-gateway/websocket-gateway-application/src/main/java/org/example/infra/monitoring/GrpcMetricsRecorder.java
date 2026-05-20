package org.example.infra.monitoring;

import io.grpc.Status;

public interface GrpcMetricsRecorder {
    void recordChatMessageSaveError(Status.Code code);

    void recordChatMessageHardDeleteError(Status.Code code);
}
