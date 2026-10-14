-- Additive migration for the Sprint 3 payment schema (#30-#32, #36-#40), which
-- only ever reached init.sql. A database created before 28 September (staging)
-- therefore had no ledger, so 006_escrow.sql failed on it.
-- Safe on the live database and on every rerun: each change is skipped when
-- it is already there. It sorts before 003 so later migrations can rely on it.

USE trade;

-- oms_order.payment_method. MySQL 8.0 has no ADD COLUMN IF NOT EXISTS.
SET @has_payment_method = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oms_order' AND COLUMN_NAME = 'payment_method');
SET @ddl = IF(@has_payment_method = 0,
    'ALTER TABLE `oms_order` ADD COLUMN `payment_method` VARCHAR(20) NULL COMMENT ''balance, alipay, wechat, bank'' AFTER `total_amount`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- The payment ledger, as init.sql defines it today.
CREATE TABLE IF NOT EXISTS `oms_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `amount` DECIMAL(12,2) NOT NULL COMMENT 'Always positive; direction is given by transaction_type',
    `payment_method` VARCHAR(20) NULL COMMENT 'balance, alipay, wechat, bank',
    `transaction_type` VARCHAR(20) NOT NULL COMMENT 'PAYMENT, PAYOUT, REFUND',
    `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS, FAILED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_txn_order` (`order_id`),
    CONSTRAINT `fk_txn_order` FOREIGN KEY (`order_id`) REFERENCES `oms_order`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only ledger of payments, payouts and refunds. What is still held against an order is the payments less the payouts and refunds.';

-- Order-state notifications (Observer, #40).
CREATE TABLE IF NOT EXISTS system_notification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    content VARCHAR(500) NOT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_user (user_id, read_flag, created_at),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_order FOREIGN KEY (order_id) REFERENCES oms_order(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Persisted system notifications, one row per recipient.';

-- The seeded test sellers' payment password, as init.sql seeds it, so balance
-- payment can be demonstrated. Only fills a missing one; never overwrites.
UPDATE users SET payment_password_hash = '$2a$12$qKa0QH..hifzPlSsE6U.Q.4gGjw513N7.xWFpmr5Q.cp12YhGY7lG'
WHERE id IN (101, 102, 103, 104, 105) AND payment_password_hash IS NULL;

SELECT 'Sprint 3 payment schema present.' AS status;
