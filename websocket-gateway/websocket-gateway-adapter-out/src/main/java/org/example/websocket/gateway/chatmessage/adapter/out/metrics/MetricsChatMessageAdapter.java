package org.example.websocket.gateway.chatmessage.adapter.out.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.example.common.grpc.exception.GrpcFailureCode;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageMetricsPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricsChatMessageAdapter implements ChatMessageMetricsPort {

    private static final String METRIC_NAME = "ws.grpc.client.errors";

    private static final String TARGET_TAG = "target";
    private static final String TARGET_CHAT_SERVICE = "chat-service";

    private static final String METHOD_TAG = "method";
    private static final String METHOD_SAVE = "save";
    private static final String METHOD_HARD_DELETE = "hardDelete";

    private static final String CODE_TAG = "code";

    private final MeterRegistry meterRegistry;

    @Override
    public void recordSaveFailure(GrpcFailureCode code) {
        record(METHOD_SAVE, code);
    }

    @Override
    public void recordHardDeleteFailure(GrpcFailureCode code) {
        record(METHOD_HARD_DELETE, code);
    }

    private void record(String method, GrpcFailureCode code) {
        meterRegistry.counter(
                METRIC_NAME,
                TARGET_TAG, TARGET_CHAT_SERVICE,
                METHOD_TAG, method,
                CODE_TAG, code.name()
        ).increment();
    }
}