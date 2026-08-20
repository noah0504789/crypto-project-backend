package org.example.common.grpc.client;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public final class GrpcFutures {

    private GrpcFutures() {
    }

    public static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> grpcFuture) {
        Objects.requireNonNull(grpcFuture, "grpcFuture");

        CompletableFuture<T> resultFuture = new CompletableFuture<>();
        Futures.addCallback(grpcFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                if (result == null) {
                    resultFuture.completeExceptionally(new IllegalStateException("gRPC returned null"));
                    return;
                }

                resultFuture.complete(result);
            }

            @Override
            public void onFailure(Throwable error) {
                resultFuture.completeExceptionally(error);
            }
        }, MoreExecutors.directExecutor());

        resultFuture.whenComplete((result, error) -> {
            if (resultFuture.isCancelled()) grpcFuture.cancel(true);
        });

        return resultFuture;
    }

    public static <T, R> CompletableFuture<R> map(
            CompletableFuture<T> source,
            Function<? super T, ? extends R> resultMapper
    ) {
        return map(source, resultMapper, Function.identity());
    }

    public static <T, R> CompletableFuture<R> map(
            CompletableFuture<T> source,
            Function<? super T, ? extends R> resultMapper,
            Function<Throwable, ? extends Throwable> errorMapper
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(resultMapper, "resultMapper");
        Objects.requireNonNull(errorMapper, "errorMapper");

        CompletableFuture<R> resultFuture = new CompletableFuture<>();
        source.whenComplete((result, error) -> {
            if (source.isCancelled()) {
                resultFuture.cancel(false);
                return;
            }

            if (error != null) {
                completeExceptionally(resultFuture, errorMapper, unwrap(error));
                return;
            }

            try {
                resultFuture.complete(resultMapper.apply(result));
            } catch (Throwable mappingError) {
                resultFuture.completeExceptionally(mappingError);
            }
        });

        resultFuture.whenComplete((result, error) -> {
            if (resultFuture.isCancelled()) source.cancel(true);
        });

        return resultFuture;
    }

    public static <T> T join(CompletableFuture<T> future) {
        Objects.requireNonNull(future, "future");

        try {
            return future.join();
        } catch (CompletionException error) {
            Throwable cause = unwrap(error);
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error fatalError) throw fatalError;
            throw error;
        }
    }

    private static <R> void completeExceptionally(
            CompletableFuture<R> resultFuture,
            Function<Throwable, ? extends Throwable> errorMapper,
            Throwable error
    ) {
        try {
            resultFuture.completeExceptionally(errorMapper.apply(error));
        } catch (Throwable mappingError) {
            resultFuture.completeExceptionally(mappingError);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
