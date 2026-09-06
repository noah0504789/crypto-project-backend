package org.example.notification.contract.event;

/** 가격 알림의 탐지 원본. 소비 서비스와 프론트가 같은 모양으로 읽는다. */
public record PriceAlertData(
        String code,
        Double price,
        Double avgPrice,
        Integer avgInterval,
        Double changeRate,
        String threshold,
        Long occurredAtMs
) {
}
