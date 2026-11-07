ALTER TABLE users ADD COLUMN address VARCHAR(500);
ALTER TABLE orders ADD COLUMN customer_name VARCHAR(150);
ALTER TABLE orders ADD COLUMN customer_phone VARCHAR(30);
ALTER TABLE orders ADD COLUMN customer_address VARCHAR(500);
UPDATE orders o SET customer_name = u.full_name, customer_phone = u.phone
FROM users u WHERE u.user_id = o.user_id;
