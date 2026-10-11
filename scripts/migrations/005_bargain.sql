-- Additive migration for the bargain module (#70, #71, PR #104); the same tables as init.sql.
-- Only CREATE TABLE IF NOT EXISTS, so it is safe on the live database and on every rerun.

-- =================================================================
-- Sprint 4: bargain campaigns (WP4)
-- =================================================================
-- Additive migration: safe for an existing Sprint 3 database. Do not rerun
-- init.sql on live data.
USE trade;

-- A buyer-run price-cut campaign on one listing. Holds no stock and takes no
-- money: expiring or converting one releases nothing.
CREATE TABLE IF NOT EXISTS bargain_campaign (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL COMMENT 'The buyer running the campaign',
    start_price DECIMAL(10,2) NOT NULL COMMENT 'Price the campaign opens at',
    current_price DECIMAL(10,2) NOT NULL COMMENT 'Live price after helpers',
    floor_price DECIMAL(10,2) NOT NULL COMMENT 'Never crossed, however many helpers',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, EXPIRED, CONVERTED',
    deadline DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_campaign_product (product_id, status),
    INDEX idx_campaign_buyer (buyer_id, status),
    CONSTRAINT fk_campaign_product FOREIGN KEY (product_id) REFERENCES pms_product(id),
    CONSTRAINT fk_campaign_buyer FOREIGN KEY (buyer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Buyer-run price-cut campaign on one listing.';

-- One help per person per campaign, enforced by the unique key.
CREATE TABLE IF NOT EXISTS bargain_campaign_helper (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_helper_campaign_user (campaign_id, user_id),
    CONSTRAINT fk_helper_campaign FOREIGN KEY (campaign_id) REFERENCES bargain_campaign(id) ON DELETE CASCADE,
    CONSTRAINT fk_helper_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Who pushed a campaign down a step.';

-- One negotiation thread is (product_id, buyer_id); the latest entry's status
-- is the state of the thread. Nothing is reserved until an offer is accepted.
CREATE TABLE IF NOT EXISTS bargain_offer (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL COMMENT 'The buyer side of the thread',
    seller_id BIGINT NOT NULL,
    offer_from VARCHAR(10) NOT NULL COMMENT 'BUYER or SELLER - who made this entry',
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, COUNTERED, ACCEPTED, REJECTED',
    order_id BIGINT NULL COMMENT 'Set on acceptance - the negotiated order',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_offer_thread (product_id, buyer_id, created_at),
    INDEX idx_offer_seller (seller_id),
    CONSTRAINT fk_offer_product FOREIGN KEY (product_id) REFERENCES pms_product(id),
    CONSTRAINT fk_offer_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT fk_offer_seller FOREIGN KEY (seller_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Offer thread entries; accepting one creates the order.';
