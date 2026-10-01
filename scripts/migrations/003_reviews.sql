-- Additive migration: safe for an existing Sprint 2 database. Do not rerun init.sql on live data.
CREATE TABLE IF NOT EXISTS review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_rating TINYINT NOT NULL,
    seller_rating TINYINT NOT NULL,
    comment TEXT NULL,
    review_images JSON NULL,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_review_order (order_id),
    INDEX idx_review_product (product_id, created_at),
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES oms_order(id),
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES pms_product(id),
    CONSTRAINT fk_review_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT fk_review_seller FOREIGN KEY (seller_id) REFERENCES users(id),
    CONSTRAINT ck_review_product_rating CHECK (product_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_review_seller_rating CHECK (seller_rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
