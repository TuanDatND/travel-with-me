# Kết quả demo member 4 → member 5

Ngày 2026-10-10. Nhánh `feature/tour-schedule-integration`.

**22/22 kiểm tra HTTP thật PASS.** Backend demo: http://localhost:8082; database PostgreSQL local `tour_schedule_demo`. Không thay dữ liệu backend 8081.

## Dữ liệu và giới hạn

- Tài khoản/role và chi nhánh là fixture local; đăng nhập lấy JWT thật từ API member 1.
- Tour và guide tạo bằng API member 4; công bố, tạo lịch, đăng ký và hủy đều gọi HTTP API thật.
- Metadata một ảnh mẫu được seed SQL local để đủ điều kiện công bố tour. Chưa test upload Cloudinary thật.
- Không gọi payment hoặc giả lập thanh toán. PENDING chỉ là giữ chỗ.

## Kết quả từng bước

| Bước | API | HTTP mong đợi/thực tế |
| --- | --- | --- |
| M4 tạo tour DRAFT | `POST /api/admin/events` | 201/201 |
| M4 tạo guide ACTIVE | `POST /api/admin/guides` | 201/201 |
| M5 từ chối lịch cho tour DRAFT | `POST /api/schedules` | 409/409 |
| M4 công bố tour qua API | `PATCH /api/admin/events/1/status` | 200/200 |
| Khách xem tour M4 | `GET /api/events/1` | 200/200 |
| Khách xem guide M4 | `GET /api/guides/1` | 200/200 |
| M5 tạo lịch từ tour và guide M4 | `POST /api/schedules` | 201/201 |
| Khách xem lịch không cần đăng nhập | `GET /api/schedules?eventId=1&status=OPEN` | 200/200 |
| Khách chưa đăng nhập bị chặn giữ chỗ | `POST /api/schedules/1/register` | 401/401 |
| CUSTOMER không được tạo lịch | `POST /api/schedules` | 403/403 |
| Guide bị chặn lịch trùng thời gian | `POST /api/schedules` | 409/409 |
| CUSTOMER giữ chỗ, bỏ qua userId/giá tự khai | `POST /api/schedules/1/register` | 201/201 |
| Lịch tăng lên 1/1 và chuyển FULL | `GET /api/schedules/1` | 200/200 |
| Đăng ký lặp bị chặn | `POST /api/schedules/1/register` | 409/409 |
| Khách thứ hai không vượt sức chứa | `POST /api/schedules/1/register` | 409/409 |
| Người khác không đọc được vé | `GET /api/participants/1` | 403/403 |
| Người khác không hủy được vé | `POST /api/participants/1/cancel` | 403/403 |
| Guide được phân công xem danh sách | `GET /api/schedules/1/participants` | 200/200 |
| Chủ vé hủy và trả chỗ | `POST /api/participants/1/cancel` | 200/200 |
| Hủy lặp không trừ chỗ lần hai | `POST /api/participants/1/cancel` | 200/200 |
| Lịch trở lại 0/1 OPEN | `GET /api/schedules/1` | 200/200 |
| Khách thứ hai đăng ký được sau khi trả chỗ | `POST /api/schedules/1/register` | 201/201 |

## Trạng thái cuối

| ID | Giá trị |
| --- | --- |
| eventId | 1 |
| guideId | 1 |
| branchId | 1 |
| scheduleId | 1 |
| cancelledParticipantId | 1 |
| activeParticipantId | 2 |
| activeUserId | 4 |

Tour 1 PUBLISHED; guide 1 ACTIVE; lịch 1 có một chỗ, đang FULL (1/1). Vé 1 của khách đầu CANCELLED. Vé 2 của khách sau PENDING, NOT_CHECKED_IN, giá 1500000.

Response thực tế khi khách sau đăng ký:

```json
{
  "status": 201,
  "message": "Đăng ký giữ chỗ tour thành công",
  "data": {
    "id": 2,
    "scheduleId": 1,
    "userId": 4,
    "registrationDate": "2026-10-10T14:18:26.346278+07:00",
    "participantStatus": "PENDING",
    "checkInStatus": "NOT_CHECKED_IN",
    "registeredPrice": 1500000.0,
    "note": "Demo nhận chỗ sau khi khách đầu hủy"
  },
  "timestamp": "2026-10-10T14:18:26.347053+07:00"
}
```

Đã xác nhận đăng ký dùng đúng principal JWT và giá tour dù request gửi userId/giá khác. Đã xác nhận đầy chỗ, đăng ký lặp, truy cập vé người khác, trùng lịch guide bị chặn; hủy lặp không trừ chỗ lần hai.
