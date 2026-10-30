-- Timestamp when the backend first verified success (manual refunds: admin confirmation).
-- Do not invent dates for historical successful transactions.
ALTER TABLE transactions ADD COLUMN succeeded_at TIMESTAMPTZ;
CREATE INDEX idx_transactions_succeeded_at ON transactions(succeeded_at, transaction_id)
    WHERE status = 'SUCCEEDED';
