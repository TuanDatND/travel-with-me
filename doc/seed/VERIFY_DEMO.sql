-- Run immediately after SEED_DEMO.sql, before changing demo data through the app.
-- Read-only assertions. Also run after the second seed execution to check repeatability.
BEGIN READ ONLY;
SET LOCAL search_path TO public;
DO $verify$
BEGIN
    IF (SELECT count(*) FROM users WHERE email LIKE '%@seed.treektour.test') <> 8
       OR (SELECT count(*) FROM branches WHERE branch_name LIKE '[SEED-v1] %') <> 3
       OR (SELECT count(*) FROM products WHERE product_name LIKE '[SEED-v1] %') <> 8
       OR (SELECT count(*) FROM tour_events WHERE event_name LIKE '[SEED-v1] %') <> 4
       OR (SELECT count(*) FROM orders o JOIN users u USING(user_id)
           WHERE u.email LIKE '%@seed.treektour.test') <> 6
       OR (SELECT count(*) FROM transactions t LEFT JOIN orders o USING(order_id)
           LEFT JOIN event_participants ep USING(participant_id)
           JOIN users u ON u.user_id=COALESCE(o.user_id,ep.user_id)
           WHERE u.email LIKE '%@seed.treektour.test') <> 11 THEN
        RAISE EXCEPTION 'Unexpected seed row counts';
    END IF;
    IF EXISTS (
        SELECT 1 FROM orders o JOIN users u USING(user_id)
        WHERE u.email LIKE '%@seed.treektour.test' AND o.total_amount <>
            (SELECT sum(oi.quantity*oi.unit_price*CASE WHEN oi.item_type='RENTAL'
                THEN oi.rental_end_date-oi.rental_start_date+1 ELSE 1 END)
             FROM order_items oi WHERE oi.order_id=o.order_id)
    ) THEN RAISE EXCEPTION 'Order total mismatch'; END IF;
    IF EXISTS (
        SELECT 1 FROM event_schedules s JOIN tour_events e USING(event_id)
        WHERE e.event_name LIKE '[SEED-v1] %' AND s.current_participants <>
            (SELECT count(*) FROM event_participants ep WHERE ep.schedule_id=s.schedule_id
             AND ep.participant_status IN ('PENDING','CONFIRMED','COMPLETED','NO_SHOW'))
    ) THEN RAISE EXCEPTION 'Schedule capacity mismatch'; END IF;
    IF EXISTS (
        SELECT 1 FROM inventory inv JOIN branches b USING(branch_id)
        WHERE b.branch_name LIKE '[SEED-v1] %'
          AND (inv.quantity_available+inv.quantity_rented<>inv.quantity_total
               OR inv.quantity_rented <> COALESCE((SELECT sum(oi.quantity) FROM order_items oi
                   JOIN orders o USING(order_id) WHERE o.branch_id=inv.branch_id
                   AND oi.product_id=inv.product_id AND oi.status='RENTED'),0))
    ) THEN RAISE EXCEPTION 'Inventory mismatch'; END IF;
    IF EXISTS (
        SELECT 1 FROM transactions t JOIN orders o USING(order_id) JOIN users u USING(user_id)
        WHERE u.email LIKE '%@seed.treektour.test' AND o.order_status IN ('CONFIRMED','IN_PROGRESS','COMPLETED')
        GROUP BY o.order_id,o.total_amount
        HAVING sum(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END)<>o.total_amount
    ) THEN RAISE EXCEPTION 'Paid order balance mismatch'; END IF;
    RAISE NOTICE 'PASS: demo counts, order totals, schedule capacity, inventory, paid balances';
END
$verify$;
COMMIT;
