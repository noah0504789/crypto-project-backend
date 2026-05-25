package org.example.marketdetection.upbit.event;

public record UpbitTickerValue(
        Double price,
        Long timestamp
) {
}