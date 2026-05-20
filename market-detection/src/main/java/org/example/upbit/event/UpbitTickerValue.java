package org.example.upbit.event;

public record UpbitTickerValue(
        Double price,
        Long timestamp
) {
}