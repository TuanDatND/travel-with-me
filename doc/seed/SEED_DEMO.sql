-- TreekTour demo v1 / PostgreSQL. Apply all Flyway migrations first.
-- Manual opt-in only; NOT a Flyway migration. Password for all accounts: Admin@123
-- Atomic, repeatable as a whole, no fixed IDs and no updates to existing records.
BEGIN;
SET LOCAL search_path TO public;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
SELECT pg_advisory_xact_lock(20261107, 301);

DO $seed$
DECLARE
    u BIGINT[] := '{}'; b BIGINT[] := '{}'; p BIGINT[] := '{}';
    g BIGINT[] := '{}'; e BIGINT[] := '{}'; s BIGINT[] := '{}';
    id BIGINT; oid BIGINT; pid BIGINT; role_name_seed TEXT; email_seed TEXT;
    i INTEGER; j INTEGER; amount_seed NUMERIC; order_state TEXT; item_state TEXT;
    participant_state TEXT; start_at TIMESTAMPTZ; end_at TIMESTAMPTZ;
    ref UUID; created TIMESTAMPTZ; pay_at TIMESTAMPTZ;
    names TEXT[] := ARRAY['Admin Demo','Staff HCM Demo','Staff Dalat Demo',
        'Guide An Demo','Guide Binh Demo','Customer Lan Demo','Customer Minh Demo','Customer Ha Demo'];
    emails TEXT[] := ARRAY['admin','staff.hcm','staff.dalat','guide.an','guide.binh',
        'customer.lan','customer.minh','customer.ha'];
    roles_seed TEXT[] := ARRAY['ADMIN','STAFF','STAFF','GUIDE','GUIDE','CUSTOMER','CUSTOMER','CUSTOMER'];
    products_seed TEXT[] := ARRAY['Leu 2 nguoi','Balo trekking 40L','Tui ngu','Gay trekking',
        'Den deo dau','Ao mua','Binh nuoc','Tham cach nhiet'];
    sale_prices NUMERIC[] := ARRAY[1800000,950000,650000,350000,280000,120000,180000,250000];
    rental_prices NUMERIC[] := ARRAY[120000,70000,50000,30000,20000,NULL,NULL,25000];
    event_names TEXT[] := ARRAY['Trekking Ta Nang','Camping Tri An','Trekking Langbiang','Camping Dau Tieng'];
    prices NUMERIC[] := ARRAY[1500000,850000,950000,750000];
BEGIN
    -- The admin email is the batch marker; never repair or overwrite an edited batch.
    IF EXISTS (SELECT 1 FROM users WHERE email = 'admin@seed.treektour.test') THEN
        IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@seed.treektour.test'
                       AND full_name = '[SEED-v1] Admin Demo') THEN
            RAISE EXCEPTION 'Seed email collision: admin@seed.treektour.test';
        END IF;
        RAISE NOTICE 'Demo v1 already installed; skipped entire batch.';
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE email = ANY (
        ARRAY(SELECT x || '@seed.treektour.test' FROM unnest(emails) x))) THEN
        RAISE EXCEPTION 'Seed email collision. No data inserted.';
    END IF;

    INSERT INTO roles(role_name, description)
    VALUES ('ADMIN','Administrator'),('STAFF','Branch staff'),('GUIDE','Tour guide'),('CUSTOMER','Customer')
    ON CONFLICT (role_name) DO NOTHING;
    FOR i IN 1..8 LOOP
        role_name_seed := roles_seed[i]; email_seed := emails[i] || '@seed.treektour.test';
        INSERT INTO users(role_id, full_name, email, phone, password, status)
        SELECT role_id, '[SEED-v1] ' || names[i], email_seed, '090000000' || i,
            '$2a$10$bdR6b.gKXe5twChnidwdzO0hvbhIrFhMd1OoBmdb4ULRQ6UYyVOrO', 'ACTIVE'
        FROM roles WHERE role_name = role_name_seed RETURNING user_id INTO id;
        u := array_append(u,id);
    END LOOP;
    FOR i IN 1..3 LOOP
        INSERT INTO branches(branch_name,phone,address,status)
        VALUES ('[SEED-v1] ' || (ARRAY['Ho Chi Minh','Da Lat','Da Nang'])[i],
            '028000000' || i, 'Demo address ' || i, 'ACTIVE') RETURNING branch_id INTO id;
        b := array_append(b,id);
    END LOOP;
    INSERT INTO staff(branch_id,user_id,position,status)
    VALUES (b[1],u[2],'Operations','ACTIVE'),(b[2],u[3],'Operations','ACTIVE');
    FOR i IN 1..2 LOOP
        INSERT INTO tour_guides(user_id,experience_years,specialization,status,bio,languages)
        VALUES (u[i+3],i+2,'TREKKING / CAMPING','ACTIVE',
            'Demo guide profile for system testing.',ARRAY['Vietnamese','English']) RETURNING guide_id INTO id;
        g := array_append(g,id);
    END LOOP;
    FOR i IN 1..8 LOOP
        INSERT INTO products(product_name,category,sale_price,rental_price_per_day,status)
        VALUES ('[SEED-v1] ' || products_seed[i],
            (ARRAY['TENT','BACKPACK','SLEEPING_BAG','ACCESSORIES','ACCESSORIES','CLOTHING','ACCESSORIES','ACCESSORIES'])[i],
            sale_prices[i],rental_prices[i],'ACTIVE') RETURNING product_id INTO id;
        p := array_append(p,id);
        FOR j IN 1..3 LOOP
            INSERT INTO inventory(branch_id,product_id,quantity_total,quantity_available,quantity_rented,status)
            VALUES (b[j],id,20,20,0,'ACTIVE');
        END LOOP;
    END LOOP;
    FOR i IN 1..4 LOOP
        INSERT INTO tour_events(event_name,event_type,description,location,base_price,status)
        VALUES ('[SEED-v1] ' || event_names[i], CASE WHEN i IN (1,3) THEN 'TREKKING' ELSE 'CAMPING' END,
            'Demo content for testing only.',(ARRAY['Lam Dong','Dong Nai','Lam Dong','Tay Ninh'])[i],
            prices[i],CASE WHEN i=4 THEN 'DRAFT' ELSE 'PUBLISHED' END) RETURNING event_id INTO id;
        e := array_append(e,id);
        INSERT INTO event_details(event_id,duration,difficulty_level,meeting_point,requirements,itinerary,packing_items)
        VALUES (id,480,CASE WHEN i=3 THEN 'HARD' ELSE 'EASY' END,'Demo branch meeting point',
            'Suitable fitness and trekking shoes.',
            '[{"day":1,"title":"Explore nature","summary":"Demo day trip","distanceKm":"5 km","elevation":"200 m","highlights":["Forest"],"activities":[{"time":"08:00","title":"Meet guide","location":"Meeting point","description":"Equipment briefing","icon":"meet"}]}]'::jsonb,
            '[{"text":"Trekking shoes","required":true},{"text":"Water bottle","required":true}]'::jsonb);
    END LOOP;

    -- Two future OPEN departures, one historical COMPLETED and one CANCELLED.
    FOR i IN 1..4 LOOP
        start_at := date_trunc('day',CURRENT_TIMESTAMP) +
            CASE WHEN i<=2 THEN (14*i) * INTERVAL '1 day' ELSE -14 * INTERVAL '1 day' END + INTERVAL '8 hours';
        end_at := start_at + INTERVAL '8 hours';
        INSERT INTO event_schedules(event_id,branch_id,guide_id,start_datetime,end_datetime,
            registration_closes_at,max_participants,current_participants,status,created_at)
        VALUES (e[CASE WHEN i=4 THEN 2 ELSE i END],b[1],g[CASE WHEN i=2 THEN 2 ELSE 1 END],
            start_at,end_at,start_at-INTERVAL '1 day',12,CASE WHEN i=4 THEN 0 ELSE 1 END,
            CASE WHEN i<=2 THEN 'OPEN' WHEN i=3 THEN 'COMPLETED' ELSE 'CANCELLED' END,
            CURRENT_TIMESTAMP-INTERVAL '30 days') RETURNING schedule_id INTO id;
        s := array_append(s,id);
        INSERT INTO event_schedule_history(schedule_id,new_start_datetime,new_end_datetime,new_status,change_reason,changed_by)
        VALUES (id,start_at,end_at,CASE WHEN i<=2 THEN 'OPEN' WHEN i=3 THEN 'COMPLETED' ELSE 'CANCELLED' END,
            '[SEED-v1] Initial demo snapshot',u[1]);
        participant_state := CASE WHEN i<=2 THEN 'CONFIRMED' WHEN i=3 THEN 'COMPLETED' ELSE 'CANCELLED' END;
        created := CASE WHEN i<=2 THEN CURRENT_TIMESTAMP-INTERVAL '1 day' ELSE start_at-INTERVAL '7 days' END;
        amount_seed := prices[CASE WHEN i=4 THEN 2 ELSE i END];
        INSERT INTO event_participants(schedule_id,user_id,participant_status,check_in_status,registered_price,
            registration_date,expires_at,confirmation_reference,note)
        VALUES (id,u[6+((i-1)%3)],participant_state,CASE WHEN i=3 THEN 'CHECKED_IN' ELSE 'NOT_CHECKED_IN' END,
            amount_seed,created,created+INTERVAL '15 minutes','SEED-v1-RECEIPT-' || i,'[SEED-v1] Demo registration')
        RETURNING participant_id,registration_reference INTO pid,ref;
        INSERT INTO schedule_participant_history(participant_id,registration_reference,action,old_status,new_status,
            registered_price,changed_by,reason,changed_at)
        VALUES (pid,ref,'CONFIRMED','PENDING','CONFIRMED',amount_seed,u[1],
            '[SEED-v1] Demo payment confirmation',created+INTERVAL '5 minutes');
        IF i>=3 THEN
            INSERT INTO schedule_participant_history(participant_id,registration_reference,action,old_status,new_status,
                registered_price,changed_by,reason,changed_at)
            VALUES (pid,ref,participant_state,'CONFIRMED',participant_state,amount_seed,u[1],
                '[SEED-v1] Demo final state',end_at);
        END IF;
        INSERT INTO transactions(participant_id,transaction_type,amount,payment_method,status,created_at,succeeded_at)
        VALUES (pid,'PAYMENT',amount_seed,'MANUAL','SUCCEEDED',created,created+INTERVAL '5 minutes');
        IF i=4 THEN
            INSERT INTO transactions(participant_id,transaction_type,amount,payment_method,status,created_at,succeeded_at,
                refund_reason,refund_confirmed_by,refund_transfer_reference,refund_confirmation_note)
            VALUES (pid,'REFUND',amount_seed,'MANUAL','SUCCEEDED',end_at,end_at,
                'Demo schedule cancellation',u[1],'SEED-v1-TOUR-REFUND','Demo only; no real transfer');
        END IF;
    END LOOP;

    -- Pending unpaid, pending paid, confirmed rental, handed-out rental,
    -- completed sale, cancelled/refunded sale. Rental billing uses inclusive days.
    FOR i IN 1..6 LOOP
        order_state := (ARRAY['PENDING','PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED'])[i];
        item_state := (ARRAY['PENDING','PENDING','CONFIRMED','RENTED','FULFILLED','CANCELLED'])[i];
        j := CASE WHEN i IN (3,4) THEN 1 ELSE 2 END;
        amount_seed := CASE WHEN i IN (3,4) THEN rental_prices[j]*3 ELSE sale_prices[j] END;
        created := CURRENT_TIMESTAMP-INTERVAL '3 days'; pay_at := created+INTERVAL '5 minutes';
        INSERT INTO orders(user_id,branch_id,order_status,total_amount,order_date,created_at,customer_received_at)
        VALUES (u[6+((i-1)%3)],b[1],order_state,amount_seed,created,created,
            CASE WHEN i IN (4,5) THEN CURRENT_TIMESTAMP-INTERVAL '1 day' ELSE NULL END)
        RETURNING order_id INTO oid;
        INSERT INTO order_items(order_id,product_id,item_type,quantity,unit_price,rental_start_date,rental_end_date,status)
        VALUES (oid,p[j],CASE WHEN i IN (3,4) THEN 'RENTAL' ELSE 'SALE' END,1,
            CASE WHEN i IN (3,4) THEN rental_prices[j] ELSE sale_prices[j] END,
            CASE WHEN i=3 THEN CURRENT_DATE+7 WHEN i=4 THEN CURRENT_DATE-1 ELSE NULL END,
            CASE WHEN i=3 THEN CURRENT_DATE+9 WHEN i=4 THEN CURRENT_DATE+1 ELSE NULL END,item_state);
        IF i>1 THEN
            INSERT INTO transactions(order_id,transaction_type,amount,payment_method,status,created_at,succeeded_at)
            VALUES (oid,'PAYMENT',amount_seed,'MANUAL','SUCCEEDED',created,pay_at);
        END IF;
        IF i=6 THEN
            INSERT INTO transactions(order_id,transaction_type,amount,payment_method,status,created_at,succeeded_at,
                refund_reason,refund_confirmed_by,refund_transfer_reference,refund_confirmation_note)
            VALUES (oid,'REFUND',amount_seed,'MANUAL','SUCCEEDED',pay_at+INTERVAL '1 day',pay_at+INTERVAL '1 day',
                'Demo customer cancellation',u[1],'SEED-v1-ORDER-REFUND','Demo only; no real transfer');
        END IF;
    END LOOP;
    -- Only the handed-out rental and completed sale affect physical stock.
    UPDATE inventory SET quantity_available=19,quantity_rented=1 WHERE branch_id=b[1] AND product_id=p[1];
    UPDATE inventory SET quantity_total=19,quantity_available=19 WHERE branch_id=b[1] AND product_id=p[2];
    RAISE NOTICE 'Demo v1 inserted: 8 users, 3 branches, 8 products, 24 inventory rows, 4 tours, 4 schedules, 4 participants, 6 orders, 11 transactions.';
END
$seed$;

SELECT u.user_id,u.email,r.role_name,u.status
FROM users u JOIN roles r USING (role_id)
WHERE u.email LIKE '%@seed.treektour.test' ORDER BY u.user_id;
COMMIT;
