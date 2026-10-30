ALTER TABLE transactions
    ADD COLUMN refund_confirmed_by BIGINT REFERENCES users(user_id),
    ADD COLUMN refund_transfer_reference VARCHAR(200),
    ADD COLUMN refund_confirmation_note VARCHAR(2000);
