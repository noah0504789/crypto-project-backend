package org.example.marketdetection.upbit.event;

import org.example.common.event.TypedKey;

public final class UpbitTickerAlertPayloadKeys {

    private UpbitTickerAlertPayloadKeys() {
    }

    public static final TypedKey<String> CODE = new TypedKey<>("code", String.class);
    public static final TypedKey<Double> PRICE = new TypedKey<>("price", Double.class);
    public static final TypedKey<Long> TIMESTAMP = new TypedKey<>("timestamp", Long.class);
    public static final TypedKey<Integer> AVG_INTERVAL = new TypedKey<>("avgInterval", Integer.class);
    public static final TypedKey<Double> AVG_PRICE = new TypedKey<>("avgPrice", Double.class);
    public static final TypedKey<Double> CHANGE_RATE = new TypedKey<>("changeRate", Double.class);
}