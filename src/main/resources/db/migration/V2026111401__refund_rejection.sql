-- Rejected requests retain audit history without contributing to refunded money.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS refund_rejected_by BIGINT REFERENCES users(user_id),
    ADD COLUMN IF NOT EXISTS refund_rejected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refund_rejection_reason VARCHAR(2000);

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_status_check
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REJECTED'));
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS ck_transactions_refund_rejection;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_refund_rejection
    CHECK (status <> 'REJECTED' OR (
        transaction_type = 'REFUND' AND payment_method = 'MANUAL'
        AND refund_rejected_by IS NOT NULL AND refund_rejected_at IS NOT NULL
        AND refund_rejection_reason IS NOT NULL AND length(trim(refund_rejection_reason)) > 0
        AND succeeded_at IS NULL));
