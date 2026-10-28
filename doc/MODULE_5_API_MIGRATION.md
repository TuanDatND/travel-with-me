# Module 5 — API naming và contract hiện tại

Cập nhật: 2026-10-28. Theo yêu cầu người dùng, URL module 5 không chứa `me`,
`my-assignments` hoặc `assigned-to-me`. URI thể hiện tài nguyên; collection dùng danh từ
số nhiều, subresource đơn như status/attendance/history giữ danh từ phù hợp.
Không có alias hoặc operation deprecated. Đây là thay đổi HTTP breaking có chủ đích.

Nguyên tắc tham khảo: [Microsoft REST API design](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design).
Việc không dùng `me` là quy ước cụ thể của module theo yêu cầu, không phải tuyên bố REST cấm từ này.

## Danh sách endpoint chính thức

| Method | URI | Mục đích / quyền |
| --- | --- | --- |
| GET | /api/schedules | Lọc lịch công khai; giữ phạm vi tour ẩn của ADMIN/STAFF |
| POST | /api/schedules | Tạo lịch; ADMIN/STAFF đúng chi nhánh |
| GET | /api/schedules/{scheduleId} | Chi tiết lịch; giữ quy tắc xem tour |
| PATCH | /api/schedules/{scheduleId} | Cập nhật guide/sức chứa; ADMIN/STAFF đúng chi nhánh |
| POST | /api/schedules/{scheduleId}/reschedulings | Dời lịch, ghi history; ADMIN/STAFF đúng chi nhánh |
| PATCH | /api/schedules/{scheduleId}/status | Đổi trạng thái có lý do; ADMIN/STAFF đúng chi nhánh |
| GET | /api/schedules/{scheduleId}/history | Lịch sử; ADMIN/STAFF đúng chi nhánh/GUIDE được phân công |
| GET | /api/guide-assignments | Danh sách phân công của GUIDE lấy từ JWT |
| GET | /api/guide-assignments/{scheduleId} | Chi tiết lịch được phân công cho GUIDE hiện tại |
| POST | /api/schedules/{scheduleId}/participants | CUSTOMER đăng ký chính mình |
| GET | /api/schedules/{scheduleId}/participants | Roster; ADMIN/STAFF đúng chi nhánh/GUIDE được phân công |
| GET | /api/participants | Mặc định vé của user từ JWT; userId tùy chọn để lọc có kiểm quyền |
| GET | /api/participants/{participantId} | Chi tiết vé; chủ vé/ADMIN/STAFF đúng chi nhánh |
| POST | /api/participants/{participantId}/cancellations | Hủy vé; chủ vé/ADMIN/STAFF đúng chi nhánh |
| POST | /api/participants/{participantId}/manual-payment-confirmations | Xác nhận thủ công; ADMIN/STAFF đúng chi nhánh |
| PATCH | /api/participants/{participantId}/attendance | Điểm danh; ADMIN/STAFF đúng chi nhánh/GUIDE được phân công |
| GET | /api/participants/{participantId}/history | Lịch sử vé; chủ vé/ADMIN/STAFF đúng chi nhánh |
| GET | /api/schedule-payment-reviews | Đối soát; ADMIN hoặc STAFF truyền branchId đúng chi nhánh |

Swagger chia tag Schedules, Guide assignments, Participants, kèm operation summary.
Placeholder dùng scheduleId/participantId; field JSON response `id` và các ID hiện tại không đổi.
GuideAssignmentController vẫn thuộc module schedule, không sửa controller/service Tour.
Assignment là view phân công của người đăng nhập, dùng scheduleId xác định mục; không thêm bảng/ID mới.

## Xác định người dùng và phân quyền

- GET /api/guide-assignments chỉ dành cho GUIDE active; backend lấy userId từ JWT,
  áp dụng scope trước phân trang. Không nhận guideId/userId để giả danh người khác.
- GET /api/participants không có userId: gọi findMine, trả vé của người đăng nhập.
- GET /api/participants?userId=123: chủ user hoặc ADMIN xem; STAFF chỉ nhận vé thuộc
  chi nhánh đang quản lý, lọc SQL trước phân trang; CUSTOMER đọc user khác trả 403.
- Anonymous nhận 401 với các API bảo vệ. Chính sách service/resource không đổi.
- Endpoint Account /api/users/me dùng để tải hồ sơ vẫn thuộc module Account;
  lượt thay đổi này không đổi API Account hoặc cách frontend nạp phiên đăng nhập.

## Request/response

Giữ ApiResponse<T>, JSON fields, mảng data và các mã lỗi nghiệp vụ. List có page >= 0,
size 1..100, default page=0/size=50; frontend dùng size=20.

PATCH lịch nhận từng trường:

```json
{"maxParticipants":20}
```

Bỏ guideId để giữ phân công; `{"guideId":null}` gỡ phân công; ID nếu truyền phải dương.
Bỏ maxParticipants để giữ sức chứa; null/0, request rỗng hoặc sức chứa nhỏ hơn số chỗ
đang giữ trả 400. Khóa lịch trước khi hợp nhất dữ liệu và kiểm tra nghiệp vụ.

POST reschedulings giữ request dời lịch:

```json
{"newStartDatetime":"2026-12-20T07:00:00+07:00","newEndDatetime":"2026-12-20T17:00:00+07:00","changeReason":"Weather"}
```

registrationClosesAt tùy chọn giữ logic cũ. Operation áp dụng đồng bộ, ghi history,
trả HTTP 200 + ScheduleResponse; không thêm workflow bất đồng bộ hoặc resource DB mới.

POST cancellations giữ body tùy chọn cho chủ vé, quản lý hủy thay khách bắt buộc có lý do:

```json
{"reason":"Không thể tham gia"}
```

Trả HTTP 200 + ParticipantResponse, giữ xử lý gọi lặp, hạn hủy/điểm danh và review thanh toán.
Không xóa vé, không tự hoàn tiền.

PATCH attendance nhận status CHECKED_IN hoặc NO_SHOW; body/status bắt buộc, giá trị khác trả 400.
Response ParticipantResponse giữ nguyên checkInStatus/participantStatus theo nghiệp vụ cũ.

POST manual-payment-confirmations giữ UUID/chứng từ:

```json
{"registrationReference":"UUID hiện tại","paymentReference":"BANK-RECEIPT-001"}
```

Giữ validation thời hạn, UUID, chống lặp và chặn vé đã có transaction; HTTP 200 trả trạng thái vé.
Contract backend ParticipantPaymentService.confirm và authority SCHEDULE_PAYMENT không đổi,
không mở endpoint HTTP cho contract nội bộ này.

## Các URL đã loại bỏ

Không có handler/alias cho 11 operation legacy trước đó: register, my-assignments (list/detail),
histories (lịch/vé), PUT lịch, by-user, confirm-payment, check-in, no-show, participants/payment-reviews.
Các URL trung gian schedules/assigned-to-me (list/detail), participants/me,
schedules/{id}/reschedule và participants/{id}/cancel cũng đã loại bỏ.
Không tạo endpoint guides/me/schedules hoặc users/me/participants.

Client phải chuyển sang bảng chính thức. API đã loại bỏ không thực thi nghiệp vụ;
HTTP lỗi phụ thuộc method/path/security/xác thực, không cam kết một mã lỗi duy nhất.
Backend đang chạy cần restart bản build mới và Swagger cần tải lại để thấy tên mới.

## Phạm vi và kiểm thử

- Backend: controller/security, contract test và tài liệu module 5.
- Frontend: chỉ features/schedules/api.ts và api.test.ts để đổi URL/method body contract;
  không sửa trang, nghiệp vụ module khác hoặc thay đổi đang có của người dùng.
- Không đổi schema, 9 migration, PK/FK, JSON, transaction/locking, state machine hoặc Payment contract.
- compileJava + compileTestJava và toàn bộ **183 tests PASS** (34 Schedule/59 Payment),
  0 failures/errors/skipped trên PostgreSQL 17.11 riêng module5_resource_api_20261028;
  Flyway áp dụng/validate 9 migrations, Hibernate validate bật.
- FE 21 tests module 5, lint và build PASS; cảnh báo bundle > 500 kB hiện hữu.
- Test OpenAPI kiểm tra domain ID placeholders, không có URI chứa me/assigned-to-me,
  và không có các operation legacy. Test quyền/branch/guide/owner, cutoff, audit/idempotence,
  cạnh tranh giữ chỗ và Payment được giữ trên các URI mới.
- MockMvc dùng security/service/database thật; test FE dùng fetch giả để kiểm tra contract.
  Chưa kiểm thử browser/provider thật hoặc deploy.

Lệnh BE với database test riêng đã tạo:

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55485/module5_resource_api_20261028'
$env:DB_USERNAME = 'module5_test'
$env:DB_PASSWORD = ''
$env:FLYWAY_ENABLED = 'true'
$env:SPRING_FLYWAY_VALIDATE_ON_MIGRATE = 'true'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1'
.\gradlew.bat compileJava compileTestJava test --offline --console=plain
```

Xem MODULE_INTEGRATION_STATUS.md cho kết quả kiểm tra cuối và lịch sử thay đổi.
