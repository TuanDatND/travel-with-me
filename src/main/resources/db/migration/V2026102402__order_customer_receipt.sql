-- Receipt is confirmed by the order owner, independently of staff handover.
ALTER TABLE orders ADD COLUMN customer_received_at TIMESTAMPTZ;
