create database if not exists common;

CREATE TABLE IF NOT EXISTS common.outbox (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     id VARCHAR(36),
     aggregate_type varchar(100) not null,
     event_type varchar(100) not null,
     payload JSON,
     transaction_id char(36) not null,
     type CHAR(20) NOT NULL DEFAULT 'GENERAL',
     status CHAR(20) NOT NULL DEFAULT 'PENDING',
     recoverable boolean default false,
     created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
     updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
     PRIMARY KEY (id, status),
     INDEX idx_type_status_created_at_txid (type, status, created_at, transaction_id)
)
PARTITION BY LIST COLUMNS (status) (
    PARTITION p_pending values in ('PENDING'),
    PARTITION p_in_process values in ('IN_PROCESS'),
    PARTITION p_completed values in ('COMPLETED'),
    PARTITION p_failed values in ('FAILED')
);
