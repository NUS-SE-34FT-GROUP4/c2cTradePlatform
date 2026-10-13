-- Additive migration for holding a payment until the buyer confirms receipt
-- (#109). No table or column changes: the ledger already stores a type per
-- row, and PAYOUT is a third value in the same VARCHAR column.
-- Only comment changes, so it is safe on the live database and on every rerun.

USE trade;

-- The column's own documentation is what a reader of the live schema sees, so
-- it has to list the value the application now writes.
ALTER TABLE `oms_transaction`
    MODIFY COLUMN `transaction_type` VARCHAR(20) NOT NULL
        COMMENT 'PAYMENT, PAYOUT, REFUND';

ALTER TABLE `oms_transaction`
    COMMENT = 'Append-only ledger of payments, payouts and refunds. What is still held against an order is the payments less the payouts and refunds.';

SELECT 'Escrow ledger comments updated.' AS status;
