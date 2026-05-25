package org.example.marketdetection.upbit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum UpbitWebsocketType {

    TICKER("ticker"),
    TRADE("trade"),
    ORDERBOOK("orderbook");

    private final String value;

    public static Optional<UpbitWebsocketType> fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst();
    }
}