-- Dữ liệu demo, không phải báo giá hay hướng dẫn tổ chức chuyến đi thực tế.
-- Supabase > SQL Editor > New query: dán toàn bộ file và Run một lần.
-- Chỉ tạo tour DRAFT và chi tiết; không tạo ảnh, lịch, chi nhánh hoặc đăng ký.
-- Chạy lại tuần tự sẽ bỏ qua tên tour đã tồn tại, không ghi đè dữ liệu.
BEGIN;

WITH seed(event_name, event_type, description, location, base_price,
          duration, difficulty_level, meeting_point, requirements) AS (
    VALUES
        ('[DEMO-LOCATION] Trekking Tà Năng', 'TREKKING',
         'Tour demo khám phá cung đường và cảnh quan Tà Năng.',
         'Tà Năng', 1500000.00, 2880, 'MODERATE',
         'Điểm tập trung demo TerraPeak tại TP. Hồ Chí Minh',
         'Giày trekking, áo mưa, nước uống và thể lực phù hợp.'),
        ('[DEMO-LOCATION] Trekking núi Bà Đen', 'TREKKING',
         'Tour demo leo núi trong ngày tại khu vực núi Bà Đen.',
         'Núi Bà Đen', 650000.00, 600, 'HARD',
         'Điểm hẹn demo tại chân núi Bà Đen',
         'Giày bám tốt, găng tay, nước uống và thể lực tốt.'),
        ('[DEMO-LOCATION] Camping hồ Trị An', 'CAMPING',
         'Tour demo cắm trại, ngắm cảnh và sinh hoạt ngoài trời.',
         'Khu vực hồ Trị An', 850000.00, 1440, 'EASY',
         'Điểm tập trung demo TerraPeak tại Biên Hòa',
         'Đồ dùng cá nhân, áo khoác và thuốc chống côn trùng.'),
        ('[DEMO-LOCATION] Camping hồ Dầu Tiếng', 'CAMPING',
         'Tour demo cắm trại qua đêm tại khu vực hồ Dầu Tiếng.',
         'Khu vực hồ Dầu Tiếng', 750000.00, 1440, 'EASY',
         'Điểm tập trung demo TerraPeak tại TP. Hồ Chí Minh',
         'Đồ dùng cá nhân, áo mưa và tuân thủ hướng dẫn an toàn.'),
        ('[DEMO-LOCATION] Trekking Langbiang', 'TREKKING',
         'Tour demo đi bộ khám phá cảnh quan khu vực Langbiang.',
         'Khu vực núi Langbiang', 950000.00, 480, 'MODERATE',
         'Điểm tập trung demo TerraPeak tại Đà Lạt',
         'Giày trekking, áo khoác và nước uống.')
), inserted AS (
    INSERT INTO public.tour_events
        (event_name, event_type, description, location, base_price, status)
    SELECT s.event_name, s.event_type, s.description, s.location, s.base_price, 'DRAFT'
    FROM seed s
    WHERE NOT EXISTS (
        SELECT 1 FROM public.tour_events e WHERE e.event_name = s.event_name
    )
    RETURNING event_id, event_name
)
INSERT INTO public.event_details
    (event_id, duration, difficulty_level, meeting_point, requirements)
SELECT i.event_id, s.duration, s.difficulty_level, s.meeting_point, s.requirements
FROM inserted i
JOIN seed s ON s.event_name = i.event_name;

-- Kiểm tra ID vừa tạo để dùng trên Swagger.
SELECT e.event_id, e.event_name, e.event_type, e.location,
       e.base_price, e.status, d.meeting_point
FROM public.tour_events e
LEFT JOIN public.event_details d ON d.event_id = e.event_id
WHERE e.event_name IN (
    '[DEMO-LOCATION] Trekking Tà Năng',
    '[DEMO-LOCATION] Trekking núi Bà Đen',
    '[DEMO-LOCATION] Camping hồ Trị An',
    '[DEMO-LOCATION] Camping hồ Dầu Tiếng',
    '[DEMO-LOCATION] Trekking Langbiang'
)
ORDER BY e.event_id;

COMMIT;
