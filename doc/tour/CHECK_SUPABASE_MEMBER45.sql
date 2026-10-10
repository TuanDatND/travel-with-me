-- Kiểm tra demo member 4 → member 5 trên Supabase. Chỉ đọc, không thay đổi dữ liệu.
-- Tour demo: DEMO_M4_M5_20261010_142218_f348
SELECT
    e.event_id,
    e.event_name,
    e.status AS event_status,
    e.base_price,
    g.guide_id,
    g.user_id AS guide_user_id,
    g.status AS guide_status,
    s.schedule_id,
    s.branch_id,
    s.start_datetime,
    s.end_datetime,
    s.max_participants,
    s.current_participants,
    s.status AS schedule_status,
    p.participant_id,
    p.user_id AS customer_user_id,
    p.participant_status,
    p.check_in_status,
    p.registered_price,
    p.note
FROM public.tour_events e
JOIN public.event_schedules s ON s.event_id = e.event_id
LEFT JOIN public.tour_guides g ON g.guide_id = s.guide_id
LEFT JOIN public.event_participants p ON p.schedule_id = s.schedule_id
WHERE e.event_id = 4
  AND s.schedule_id = 1
ORDER BY p.participant_id;
