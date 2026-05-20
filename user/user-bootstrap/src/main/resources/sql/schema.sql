CREATE TABLE IF NOT EXISTS user.user (
    id BIGINT NOT NULL,
    public_id BINARY(16) NOT NULL,
    sub VARCHAR(255),
    nickname VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY `uk_user_public_id` (public_id),
    UNIQUE KEY `uk_user_email` (email),
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

-- 역할 테이블
CREATE TABLE IF NOT EXISTS user.role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

-- 사용자 역할 매핑 테이블 (복합 PK)
CREATE TABLE IF NOT EXISTS user.user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);
