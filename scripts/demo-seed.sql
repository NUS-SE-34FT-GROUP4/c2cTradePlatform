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
UPDATE `pms_product` SET `stock` = 20 WHERE `id` BETWEEN 1 AND 12;

-- -----------------------------------------------------------------
-- 3. The seller used by the main transaction line
-- -----------------------------------------------------------------
-- seller_lvl2 (102) owns product 4 《算法导论》, the item the script
-- buys. Its balance is the figure the audience watches: unchanged
-- after payment, credited only on confirm-receipt.
UPDATE `users` SET `balance` = 2000.00 WHERE `id` = 102;

-- -----------------------------------------------------------------
-- 4. English demo data
-- -----------------------------------------------------------------
-- The presentation is in English and the catalogue is on screen for most
-- of it, so every string an examiner can read is English here. init.sql
-- is left alone: it is shared by the whole team and other suites may
-- match on its strings. Locations are Singapore districts, which also
-- makes the S$ prices on the product page coherent.

UPDATE `users` SET `display_name` = 'System Administrator' WHERE `id` = 1;
UPDATE `users` SET `display_name` = 'Demo Buyer'           WHERE `id` = 201;

UPDATE `pms_product` SET `name` = 'iPhone 13 128GB Midnight',
    `description` = 'Used for one year. No dents, battery health 91%, original charging cable included.',
    `location` = 'Clementi' WHERE `id` = 1;
UPDATE `pms_product` SET `name` = 'ThinkPad X1 Carbon 2021 i7/16GB',
    `description` = 'Spare machine for business trips. No screen scratches, keyboard in good condition, sleeve included.',
    `location` = 'Clementi' WHERE `id` = 2;
UPDATE `pms_product` SET `name` = 'Sony WH-1000XM4 Noise Cancelling Headphones',
    `description` = 'Noise cancelling still excellent. Ear pads replaced with new ones.',
    `location` = 'Bukit Timah' WHERE `id` = 3;
UPDATE `pms_product` SET `name` = 'Introduction to Algorithms, 3rd Edition',
    `description` = 'Used for exam revision. A few pencil notes inside, all erasable.',
    `location` = 'Bukit Timah' WHERE `id` = 4;
UPDATE `pms_product` SET `name` = 'Computer Systems: A Programmer''s Perspective, 3rd Edition',
    `description` = 'The classic CSAPP, almost new. Only the first three chapters were opened.',
    `location` = 'Bukit Timah' WHERE `id` = 5;
UPDATE `pms_product` SET `name` = 'Kindle Paperwhite 4 8GB',
    `description` = 'Fully working. Comes with a cover, no dead pixels.',
    `location` = 'Jurong East' WHERE `id` = 6;
UPDATE `pms_product` SET `name` = 'University English Past Papers, 10-Year Set (6 books)',
    `description` = 'Ten years of past papers with worked answers, six books, no annotations.',
    `location` = 'Jurong East' WHERE `id` = 7;
UPDATE `pms_product` SET `name` = 'Nike Air Force 1 White, UK 8',
    `description` = 'Worn three times. Box and spare insoles included, soles barely worn.',
    `location` = 'Jurong East' WHERE `id` = 8;
UPDATE `pms_product` SET `name` = 'iPad Air 4 64GB Sky Blue',
    `description` = 'Comes with an original Apple Pencil 2. Privacy screen protector fitted.',
    `location` = 'Tampines' WHERE `id` = 9;
UPDATE `pms_product` SET `name` = 'Uniqlo Ultra Light Down Jacket, Men''s L',
    `description` = 'Bought last winter, worn only a few times. No tears or stains.',
    `location` = 'Tampines' WHERE `id` = 10;
UPDATE `pms_product` SET `name` = 'Logitech MX Master 3 Wireless Mouse',
    `description` = 'Office workhorse. Scroll wheel works, receiver and charging cable included.',
    `location` = 'Punggol' WHERE `id` = 11;
UPDATE `pms_product` SET `name` = 'Sapiens and Homo Deus Boxed Set',
    `description` = 'Genuine copies. Slight crease on the spines, pages clean.',
    `location` = 'Punggol' WHERE `id` = 12;

-- -----------------------------------------------------------------
-- 5. Readback -- paste this output into the rehearsal notes
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
