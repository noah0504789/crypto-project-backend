package org.example.market.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum PriceAlertChangeRateThreshold {

    PERCENT_3(0.03),
    PERCENT_5(0.05),
    PERCENT_7(0.07);

    private final double rate;

    public static List<PriceAlertChangeRateThreshold> matchedBy(double changeRate) {
        double absoluteChangeRate = Math.abs(changeRate);

        return Arrays.stream(values())
                .filter(value -> absoluteChangeRate >= value.rate)
                .toList();
    }
}