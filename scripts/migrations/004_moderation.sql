-- Additive migration for reports, bans and the admin audit log (#61, #63).
-- Safe to rerun: the deployment applies it on every release, like 003.
-- Contract: docs/api/moderation.md

-- users.status / ban_reason / banned_at. MySQL 8.0 has no ADD COLUMN IF NOT
-- EXISTS, so the ALTER only runs when the column is missing.
SET @has_status = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'status');
SET @ddl = IF(@has_status = 0,
    'ALTER TABLE users
         ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''ACTIVE or BANNED'',
         ADD COLUMN ban_reason VARCHAR(500) NULL COMMENT ''Shown to the user on every refusal'',
         ADD COLUMN banned_at DATETIME NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(10) NOT NULL COMMENT 'PRODUCT or USER',
    target_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL,
    detail VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN, UPHELD or DISMISSED',
    decided_by BIGINT NULL,
    decision_note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at DATETIME NULL,
    INDEX idx_report_status (status, created_at),
    INDEX idx_report_reporter (reporter_id),
    INDEX idx_report_target (target_type, target_id, status),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_decided_by FOREIGN KEY (decided_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Reports on a product or a user, and their decision';

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL COMMENT 'BAN_USER, REINSTATE_USER, UPHOLD_REPORT, DISMISS_REPORT, DELIST_PRODUCT',
    target_type VARCHAR(10) NOT NULL,
    target_id BIGINT NOT NULL,
    report_id BIGINT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created (created_at),
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES users(id),
    CONSTRAINT fk_audit_report FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Append-only record of administrator actions';
