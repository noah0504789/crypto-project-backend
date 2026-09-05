package org.example.upbitconnector.application.port.out;

/**
 * 수집 파이프라인 관측 지표. application 이 Micrometer 를 모르도록 포트로 둔다.
 */
public interface UpbitTickerMetricsPort {

    void tickerReceived(String code);

    void tickerPublished(String code, long elapsedNanos);

    void tickerPublishFailed(String code);
}
