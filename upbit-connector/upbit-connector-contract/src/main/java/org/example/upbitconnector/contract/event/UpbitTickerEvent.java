package org.example.upbitconnector.contract.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Upbit WebSocket ticker 메시지. 필드 이름은 Upbit 응답 그대로이며, 소비자(market-detection Kafka Streams)와 공유하는 계약이라
 * JSON 프로퍼티 이름을 바꾸지 않는다.
 */
public record UpbitTickerEvent(
        @JsonProperty("type") String type,
        @JsonProperty("code") String code,
        @JsonProperty("opening_price") Double openingPrice,
        @JsonProperty("high_price") Double highPrice,
        @JsonProperty("low_price") Double lowPrice,
        @JsonProperty("trade_price") Double tradePrice,
        @JsonProperty("prev_closing_price") Double prevClosingPrice,
        @JsonProperty("change") String change,
        @JsonProperty("change_price") Double changePrice,
        @JsonProperty("signed_change_price") Double signedChangePrice,
        @JsonProperty("change_rate") Double changeRate,
        @JsonProperty("signed_change_rate") Double signedChangeRate,
        @JsonProperty("trade_volume") Double tradeVolume,
        @JsonProperty("acc_trade_volume") Double accTradeVolume,
        @JsonProperty("acc_trade_volume_24h") Double accTradeVolume24h,
        @JsonProperty("acc_trade_price") Double accTradePrice,
        @JsonProperty("acc_trade_price_24h") Double accTradePrice24h,
        @JsonProperty("trade_date") String tradeDate,
        @JsonProperty("trade_time") String tradeTime,
        @JsonProperty("trade_timestamp") Long tradeTimestamp,
        @JsonProperty("ask_bid") String askBid,
        @JsonProperty("acc_ask_volume") Double accAskVolume,
        @JsonProperty("acc_bid_volume") Double accBidVolume,
        @JsonProperty("highest_52_week_price") Double highest52WeekPrice,
        @JsonProperty("highest_52_week_date") String highest52WeekDate,
        @JsonProperty("lowest_52_week_price") Double lowest52WeekPrice,
        @JsonProperty("lowest_52_week_date") String lowest52WeekDate,
        @JsonProperty("trade_status") String tradeStatus,
        @JsonProperty("market_state") String marketState,
        @JsonProperty("market_state_for_ios") String marketStateForIos,
        @JsonProperty("is_trading_suspended") Boolean isTradingSuspended,
        @JsonProperty("delisting_date") String delistingDate,
        @JsonProperty("market_warning") String marketWarning,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("stream_type") String streamType) {}
