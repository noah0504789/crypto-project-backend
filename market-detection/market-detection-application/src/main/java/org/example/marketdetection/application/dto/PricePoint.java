package org.example.marketdetection.application.dto;

/** 변동률 계산에 쓰는 시세 표본. state store 값 타입이라 필드 이름이 곧 저장 포맷이다. */
public record PricePoint(Double price, Long timestamp) {}
