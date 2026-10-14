-- =================================================================
-- C2C SecTrade - Demo fallback: put the numbers back
-- =================================================================
-- For REHEARSAL ONLY. Do not run this on 26 Oct.
--
-- Use it when a rehearsal run has left the data in a state that
-- cannot be narrated -- a half-finished refund, an order stuck part
-- way through -- and you want the next run to start from the same
-- figures as the first.
--
-- It restores balances and stock. It deletes nothing: orders, ledger
-- rows and reviews stay, because the demo is designed to tolerate
-- them (see docs/demo/demo-script.md). Redis and Elasticsearch are
-- deliberately untouched -- clearing them is what makes a reset
-- risky, and with stock at 999 a leftover reservation blocks nobody.
-- =================================================================

UPDATE `users`       SET `balance` = 99999.00 WHERE `id` = 201;  -- demo_buyer
UPDATE `users`       SET `balance` = 2000.00  WHERE `id` = 102;  -- seller_lvl2
UPDATE `pms_product` SET `stock`   = 999      WHERE `id` BETWEEN 1 AND 12;

-- Readback. A row that reads MISSING means the UPDATE above matched
-- nothing: re-running init.sql drops and recreates `users`, which deletes
-- the demo buyer. Without this check the UPDATE fails silently and the row
-- simply vanishes from the output.
SELECT 'demo_buyer (201)'  AS who,
       IFNULL((SELECT CAST(`balance` AS CHAR) FROM `users` WHERE `id` = 201),
              'MISSING -- re-run scripts/demo-seed.sql') AS balance
UNION ALL
SELECT 'seller_lvl2 (102)',
       IFNULL((SELECT CAST(`balance` AS CHAR) FROM `users` WHERE `id` = 102),
              'MISSING -- re-run init.sql, then scripts/demo-seed.sql')
UNION ALL
SELECT 'product 4 stock',
       IFNULL((SELECT CAST(`stock` AS CHAR) FROM `pms_product` WHERE `id` = 4),
              'MISSING -- re-run init.sql, then scripts/demo-seed.sql');
