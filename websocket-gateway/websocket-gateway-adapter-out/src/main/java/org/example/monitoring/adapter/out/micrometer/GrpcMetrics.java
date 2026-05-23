package org.example.monitoring.adapter.out.micrometer;

import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.example.monitoring.application.port.out.GrpcMetricsRecorder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcMetrics implements GrpcMetricsRecorder {

    private final MeterRegistry meterRegistry;

    @Override
    public void recordChatMessageSaveError(Status.Code code) {
        meterRegistry.counter(
        "ws.grpc.client.errors",
        "target", "chat-service",
               "method", "save",
               "code", code.name()
        ).increment();
    }

    @Override
    public void recordChatMessageHardDeleteError(Status.Code code) {
        meterRegistry.counter(
          "ws.grpc.client.errors",
         "target", "chat-service",
                "method", "hardDelete",
                "code", code.name()
        ).increment();
    }
}
