package org.example.common.grpc.client;

import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcFuturesUnitTest {

    @Test
    @DisplayName("gRPC Future 성공 결과를 CompletableFuture에 전달한다")
    void toCompletableFuture_shouldPropagateResult() {
        SettableFuture<String> source = SettableFuture.create();

        CompletableFuture<String> result = GrpcFutures.toCompletableFuture(source);
        source.set("response");

        assertThat(result.join()).isEqualTo("response");
    }

    @Test
    @DisplayName("gRPC Future 오류를 CompletableFuture에 전달한다")
    void toCompletableFuture_shouldPropagateError() {
        SettableFuture<String> source = SettableFuture.create();
        IllegalStateException error = new IllegalStateException("gRPC failed");

        CompletableFuture<String> result = GrpcFutures.toCompletableFuture(source);
        source.setException(error);

        CompletionException actual = assertThrows(CompletionException.class, result::join);
        assertThat(actual.getCause()).isSameAs(error);
    }

    @Test
    @DisplayName("CompletableFuture 취소를 gRPC Future에 전달한다")
    void toCompletableFuture_shouldPropagateCancellation() {
        SettableFuture<String> source = SettableFuture.create();

        CompletableFuture<String> result = GrpcFutures.toCompletableFuture(source);
        result.cancel(true);

        assertThat(source).isCancelled();
    }

    @Test
    @DisplayName("파생 Future 취소를 원본 CompletableFuture에 전달한다")
    void map_shouldPropagateCancellation() {
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<Integer> result = GrpcFutures.map(source, String::length);
        result.cancel(true);

        assertThat(source).isCancelled();
    }

    @Test
    @DisplayName("동기 경계에서는 CompletionException을 벗겨 원래 런타임 오류를 전달한다")
    void join_shouldPropagateOriginalRuntimeError() {
        IllegalStateException error = new IllegalStateException("gRPC failed");
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(error);

        IllegalStateException actual = assertThrows(IllegalStateException.class, () -> GrpcFutures.join(future));

        assertThat(actual).isSameAs(error);
    }
}
