CREATE TABLE IF NOT EXISTS `user`.`user` (
    id BIGINT NOT NULL,
    public_id BINARY(16) NOT NULL,
    sub VARCHAR(255),
    nickname VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY `uk_user_public_id` (public_id),
    UNIQUE KEY `uk_user_email` (email),
    UNIQUE KEY `uk_user_nickname` (nickname),
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS `user`.`role` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS `user`.`user_role` (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY `uk_user_role_user_id_role_id` (user_id, role_id),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (user_id) REFERENCES `user`.`user`(id) ON DELETE CASCADE,
    CONSTRAINT `fk_user_role_role` FOREIGN KEY (role_id) REFERENCES `user`.`role`(id) ON DELETE CASCADE
);

INSERT INTO `user`.`role` (name)
VALUES ('USER')
ON DUPLICATE KEY UPDATE name = name;