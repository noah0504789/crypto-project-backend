package org.example.marketdetection.contract.event;

import org.example.common.event.TypedKey;

public final class PriceAlertDetectedPayloadKeys {

    private PriceAlertDetectedPayloadKeys() {
    }

    public static final TypedKey<String> CODE = new TypedKey<>("code", String.class);
    public static final TypedKey<Double> PRICE = new TypedKey<>("price", Double.class);
    public static final TypedKey<Long> OCCURRED_AT_MS = new TypedKey<>("occurredAtMs", Long.class);
    public static final TypedKey<Integer> AVG_INTERVAL = new TypedKey<>("avgInterval", Integer.class);
    public static final TypedKey<Double> AVG_PRICE = new TypedKey<>("avgPrice", Double.class);
    public static final TypedKey<Double> CHANGE_RATE = new TypedKey<>("changeRate", Double.class);
    public static final TypedKey<String> THRESHOLD = new TypedKey<>("threshold", String.class);
}
