-- Customer-provided explanation for a manual refund. Legacy requests remain NULL.
ALTER TABLE transactions ADD COLUMN refund_reason VARCHAR(2000);
