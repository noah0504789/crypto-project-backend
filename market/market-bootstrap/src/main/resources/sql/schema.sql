CREATE TABLE IF NOT EXISTS market (
    id BIGINT NOT NULL AUTO_INCREMENT,
    market_code VARCHAR(30) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    korean_name VARCHAR(50) NOT NULL,
    english_name VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY `uk_markets_market_code` (market_code)
);

INSERT INTO market (
    market_code,
    symbol,
    korean_name,
    english_name,
    enabled
) VALUES
    ('KRW-BTC', 'BTC', '비트코인', 'Bitcoin', TRUE),
    ('KRW-ETH', 'ETH', '이더리움', 'Ethereum', TRUE),
    ('KRW-SOL', 'SOL', '솔라나', 'Solana', TRUE),
    ('KRW-XRP', 'XRP', '리플', 'Ripple', TRUE),
    ('KRW-DOGE', 'DOGE', '도지코인', 'Dogecoin', TRUE)
ON DUPLICATE KEY UPDATE
     symbol = VALUES(symbol),
     korean_name = VALUES(korean_name),
     english_name = VALUES(english_name),
     enabled = VALUES(enabled);