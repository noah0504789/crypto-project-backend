package org.example.upbit.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.common.event.KafkaEvent;

public record UpbitTickerEvent(

        @JsonProperty("type")
        String type,

        @JsonProperty("code")
        String code,

        @JsonProperty("opening_price")
        Double openingPrice,

        @JsonProperty("high_price")
        Double highPrice,

        @JsonProperty("low_price")
        Double lowPrice,

        @JsonProperty("trade_price")
        Double tradePrice,

        @JsonProperty("prev_closing_price")
        Double prevClosingPrice,

        @JsonProperty("change")
        String change,

        @JsonProperty("change_price")
        Double changePrice,

        @JsonProperty("signed_change_price")
        Double signedChangePrice,

        @JsonProperty("change_rate")
        Double changeRate,

        @JsonProperty("signed_change_rate")
        Double signedChangeRate,

        @JsonProperty("trade_volume")
        Double tradeVolume,

        @JsonProperty("acc_trade_volume")
        Double accTradeVolume,

        @JsonProperty("acc_trade_volume_24h")
        Double accTradeVolume24h,

        @JsonProperty("acc_trade_price")
        Double accTradePrice,

        @JsonProperty("acc_trade_price_24h")
        Double accTradePrice24h,

        @JsonProperty("trade_date")
        String tradeDate,

        @JsonProperty("trade_time")
        String tradeTime,

        @JsonProperty("trade_timestamp")
        Long tradeTimestamp,

        @JsonProperty("ask_bid")
        String askBid,

        @JsonProperty("acc_ask_volume")
        Double accAskVolume,

        @JsonProperty("acc_bid_volume")
        Double accBidVolume,

        @JsonProperty("highest_52_week_price")
        Double highest52WeekPrice,

        @JsonProperty("highest_52_week_date")
        String highest52WeekDate,

        @JsonProperty("lowest_52_week_price")
        Double lowest52WeekPrice,

        @JsonProperty("lowest_52_week_date")
        String lowest52WeekDate,

        @JsonProperty("trade_status")
        String tradeStatus,

        @JsonProperty("market_state")
        String marketState,

        @JsonProperty("market_state_for_ios")
        String marketStateForIos,

        @JsonProperty("is_trading_suspended")
        Boolean isTradingSuspended,

        @JsonProperty("delisting_date")
        String delistingDate,

        @JsonProperty("market_warning")
        String marketWarning,

        @JsonProperty("timestamp")
        Long timestamp,

        @JsonProperty("stream_type")
        String streamType

) implements KafkaEvent {

    @Override
    public String getPartitionKey() {
        if (code == null) {
            throw new IllegalStateException("UpbitTickerEvent code is null");
        }

        return code;
    }
}