# Demo member 4 → member 5 trên Supabase

Ngày 2026-10-10. Nhánh `feature/tour-schedule-integration`. Backend `http://localhost:8081`.

**22/22 kiểm tra HTTP thật PASS; đã xác nhận trực tiếp dữ liệu Supabase bằng SELECT.**

Nhãn dữ liệu: `DEMO_M4_M5_20261010_142218_f348`.

## Dữ liệu còn lại để kiểm tra

| Bảng | ID | Trạng thái |
| --- | --- | --- |
| tour_events | 4 | PUBLISHED, base_price=1500000 |
| tour_guides | 4 | ACTIVE, user_id=1 |
| event_schedules | 1 | FULL, current_participants=1, max_participants=1 |
| event_participants | 1 | CANCELLED, user_id=15 |
| event_participants | 2 | PENDING, user_id=16, registered_price=1500000 |

Hai tài khoản CUSTOMER demo mới được tạo qua auth API, không sửa tài khoản sẵn có. Guide demo mới liên kết tài khoản ADMIN 1 ACTIVE, không đổi role hoặc dữ liệu tài khoản đó. Chi nhánh 1 sẵn có được tham chiếu, không sửa.

## Kiểm tra trên Supabase

Mở SQL Editor và chạy nội dung `doc/tour/CHECK_SUPABASE_MEMBER45.sql`. Query chỉ SELECT, dự kiến trả hai dòng: một vé CANCELLED và một vé PENDING của cùng lịch 1.

## Từng bước HTTP

| Bước | Request | HTTP mong đợi/thực tế |
| --- | --- | --- |
| M4 tạo tour DRAFT trên Supabase | `POST /api/admin/events` | 201/201 |
| M4 tạo guide demo ACTIVE | `POST /api/admin/guides` | 201/201 |
| M5 chặn lịch của tour DRAFT | `POST /api/schedules` | 409/409 |
| M4 công bố tour qua API | `PATCH /api/admin/events/4/status` | 200/200 |
| Khách xem chi tiết tour M4 | `GET /api/events/4` | 200/200 |
| Khách xem guide M4 | `GET /api/guides/4` | 200/200 |
| M5 tạo lịch dùng tour/guide M4 | `POST /api/schedules` | 201/201 |
| Khách xem lịch công khai | `GET /api/schedules?eventId=4&status=OPEN` | 200/200 |
| Chưa đăng nhập không được đăng ký | `POST /api/schedules/1/register` | 401/401 |
| Khách không được tạo lịch | `POST /api/schedules` | 403/403 |
| Chặn trùng lịch guide | `POST /api/schedules` | 409/409 |
| Khách 1 đăng ký, server lấy JWT và giá tour | `POST /api/schedules/1/register` | 201/201 |
| Lịch FULL 1/1 sau đăng ký | `GET /api/schedules/1` | 200/200 |
| Chặn đăng ký lặp | `POST /api/schedules/1/register` | 409/409 |
| Khách 2 không vượt sức chứa | `POST /api/schedules/1/register` | 409/409 |
| Khách 2 không đọc được vé khách 1 | `GET /api/participants/1` | 403/403 |
| Khách 2 không hủy được vé khách 1 | `POST /api/participants/1/cancel` | 403/403 |
| Admin xem danh sách giữ chỗ | `GET /api/schedules/1/participants` | 200/200 |
| Khách 1 hủy và trả chỗ | `POST /api/participants/1/cancel` | 200/200 |
| Hủy lặp không trừ thêm chỗ | `POST /api/participants/1/cancel` | 200/200 |
| Lịch trở lại OPEN 0/1 | `GET /api/schedules/1` | 200/200 |
| Khách 2 đăng ký được chỗ vừa trả | `POST /api/schedules/1/register` | 201/201 |

## Kết quả truy vấn trực tiếp Supabase

```json
{
  "event": {
    "eventId": 4,
    "name": "DEMO_M4_M5_20261010_142218_f348 trekking",
    "basePrice": 1500000.0,
    "status": "PUBLISHED"
  },
  "guide": {
    "guideId": 4,
    "userId": 1,
    "status": "ACTIVE"
  },
  "schedule": {
    "scheduleId": 1,
    "eventId": 4,
    "guideId": 4,
    "currentParticipants": 1,
    "maxParticipants": 1,
    "status": "FULL"
  },
  "participants": [
    {
      "participantId": 1,
      "userId": 15,
      "status": "CANCELLED",
      "registeredPrice": 1500000.0
    },
    {
      "participantId": 2,
      "userId": 16,
      "status": "PENDING",
      "registeredPrice": 1500000.0
    }
  ]
}
```

## Giới hạn

Metadata ảnh placeholder được thêm SQL vào đúng tour demo mới tạo để đáp ứng điều kiện công bố; không thực hiện upload Cloudinary. Tour/guide tạo qua API, công bố tour, lịch, giữ chỗ và hủy đều gọi HTTP thật. Không gọi payment, không xác nhận thanh toán. Dữ liệu demo được giữ lại theo yêu cầu để kiểm tra trên Supabase.
