package org.example.upbitconnector.adapter.out.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UpbitMetricNames {

    public static final String TICKER_RECEIVED = "upbit.ticker.received";
    public static final String TICKER_PUBLISHED = "upbit.ticker.published";
    public static final String TICKER_PUBLISH_FAILED = "upbit.ticker.publish.failed";
    public static final String TICKER_PUBLISH_LATENCY = "upbit.ticker.publish";
    public static final String WEBSOCKET_RECONNECT = "upbit.websocket.reconnect";
    public static final String COLLECT_RESTART = "upbit.collect.restart";

    public static final String TAG_CODE = "code";
    public static final String TAG_REASON = "reason";

    public static final String REASON_ERROR = "error";
    public static final String REASON_COMPLETED = "completed";
}
