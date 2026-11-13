DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT c.conname INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
    WHERE t.relname = 'orders' AND a.attname = 'order_status' AND c.contype = 'c';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE orders DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE orders
    ADD CONSTRAINT orders_order_status_check
    CHECK (order_status IN ('PENDING', 'CONFIRMED', 'WITH_GUIDE', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

