-- =================================================================
-- C2C SecTrade - Demo seed data (presentation, 26 Oct 2026)
-- =================================================================
-- Run this AFTER init.sql, against the same database.
--
-- The demo is a live walk-through, repeated several times during
-- rehearsal and once on stage. Nothing here deletes history: the
-- demo is made repeatable by seeding generously rather than by
-- resetting between runs, so the order list, the ledger and the
-- seller's rating simply keep growing. Consequence for the script:
-- no absolute figure is ever quoted on stage. See
-- docs/demo/demo-script.md.
--
-- Rerunnable: every statement is an UPDATE or an upsert.
-- =================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------
-- 1. A buyer that can actually pay
-- -----------------------------------------------------------------
-- `admin` (id 1) is seeded in init.sql without a balance and without
-- a payment password, so both default to NULL/0 and it cannot
-- complete a payment at all. Rather than bend the admin account into
-- a shopper, the demo gets its own buyer.
--
-- Login password: admin123   Payment password: 123456
INSERT INTO `users`
    (`id`, `username`, `display_name`, `password_hash`, `payment_password_hash`,
     `email`, `avatar_url`, `balance`)
VALUES
    (201, 'demo_buyer', '演示买家',
     '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2',
     '$2a$12$qKa0QH..hifzPlSsE6U.Q.4gGjw513N7.xWFpmr5Q.cp12YhGY7lG',
     'demo.buyer@test.com', 'https://i.pravatar.cc/150?u=demobuyer',
     99999.00)
ON DUPLICATE KEY UPDATE
    display_name          = VALUES(display_name),
    password_hash         = VALUES(password_hash),
    payment_password_hash = VALUES(payment_password_hash),
    balance               = VALUES(balance);

INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES (201, 1)
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

-- -----------------------------------------------------------------
-- 2. Stock that survives repeated rehearsals
-- -----------------------------------------------------------------
-- init.sql seeds every product with stock 1, and its ON DUPLICATE KEY
-- clause updates only name/price/description -- rerunning it does NOT
-- put the stock back. One rehearsal purchase would otherwise leave the
-- item unbuyable.
UPDATE `pms_product` SET `stock` = 999 WHERE `id` BETWEEN 1 AND 12;

-- -----------------------------------------------------------------
-- 3. The seller used by the main transaction line
-- -----------------------------------------------------------------
-- seller_lvl2 (102) owns product 4 《算法导论》, the item the script
-- buys. Its balance is the figure the audience watches: unchanged
-- after payment, credited only on confirm-receipt.
UPDATE `users` SET `balance` = 2000.00 WHERE `id` = 102;

-- -----------------------------------------------------------------
-- 4. Readback -- paste this output into the rehearsal notes
-- -----------------------------------------------------------------
-- A row reading MISSING means init.sql has not been applied to this
-- database, so the UPDATEs above matched nothing.
SELECT 'demo buyer balance' AS item,
       IFNULL((SELECT CAST(`balance` AS CHAR) FROM `users` WHERE `id` = 201), 'MISSING') AS value
UNION ALL
SELECT 'buyer payment pwd set',
       IFNULL((SELECT IF(`payment_password_hash` IS NULL, 'NO -- BROKEN', 'yes')
               FROM `users` WHERE `id` = 201), 'MISSING')
UNION ALL
SELECT 'seller 102 balance',
       IFNULL((SELECT CAST(`balance` AS CHAR) FROM `users` WHERE `id` = 102),
              'MISSING -- run init.sql first')
UNION ALL
SELECT 'product 4 stock',
       IFNULL((SELECT CAST(`stock` AS CHAR) FROM `pms_product` WHERE `id` = 4),
              'MISSING -- run init.sql first')
UNION ALL SELECT 'orders in db',      CAST(COUNT(*) AS CHAR) FROM `oms_order`
UNION ALL SELECT 'ledger rows in db', CAST(COUNT(*) AS CHAR) FROM `oms_transaction`;
