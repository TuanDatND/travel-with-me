# Module 5 — API migration (mốc 4–5)

Ngày: 2026-10-28. Package `modules.schedule`. Giữ `ApiResponse<T>`, JSON response,
mảng `data`, enum lưu DB, PK/FK và migration. Không thêm `/v1` riêng cho module.
API mới là contract ưu tiên; API cũ còn hoạt động để client chuyển dần.

## Bảng chuyển đổi

Tiền tố chung `/api`. Các route không xuất hiện trong bảng giữ nguyên.

| Legacy (còn hỗ trợ) | Canonical | Quyền |
| --- | --- | --- |
| POST /schedules/{scheduleId}/register | POST /schedules/{scheduleId}/participants | CUSTOMER đăng ký chính JWT |
| GET /schedules/my-assignments | GET /schedules/assigned-to-me | GUIDE đang hoạt động |
| GET /schedules/my-assignments/{id} | GET /schedules/assigned-to-me/{id} | GUIDE đúng phân công |
| GET /schedules/{id}/histories | GET /schedules/{id}/history | ADMIN, STAFF đúng branch, GUIDE được phân công |
| PUT /schedules/{id} | PATCH /schedules/{id} | ADMIN, STAFF đúng branch; khác biệt partial update bên dưới |
| GET /participants/by-user/{userId} | GET /participants?userId={userId} | Chính user, ADMIN; STAFF chỉ vé thuộc branch được quản lý |
| — | GET /participants/me | ADMIN/STAFF/CUSTOMER; lấy userId từ JWT |
| PATCH /participants/{id}/confirm-payment | POST /participants/{id}/manual-payment-confirmations | ADMIN, STAFF đúng branch |
| PATCH /participants/{id}/check-in | PATCH /participants/{id}/attendance với CHECKED_IN | ADMIN, STAFF đúng branch, GUIDE được phân công |
| PATCH /participants/{id}/no-show | PATCH /participants/{id}/attendance với NO_SHOW | Như check-in; giữ quy tắc cửa sổ điểm danh |
| GET /participants/{id}/histories | GET /participants/{id}/history | Chủ vé, ADMIN, STAFF đúng branch |
| GET /participants/payment-reviews | GET /schedule-payment-reviews | ADMIN; STAFF cần branchId đúng branch |

Các alias dùng cùng service nghiệp vụ, transaction, thứ tự khóa, history và chống lặp.
Cùng một vé không thể giữ chỗ/xác nhận/ghi audit hai lần chỉ bằng đổi route.
`POST /schedules`, GET danh sách/chi tiết lịch, POST reschedule, PATCH status,
GET roster/chi tiết vé và POST cancel giữ nguyên.

## Request/response mới

### Cập nhật một phần lịch

`PATCH /api/schedules/{id}`, Content-Type `application/json`:

```json
{"maxParticipants":20}
```

- Bỏ `guideId`: giữ guide hiện tại. `guideId: null`: gỡ phân công. ID guide phải dương nếu truyền giá trị.
- Bỏ `maxParticipants`: giữ sức chứa. Truyền giá trị phải >= 1 và không ít hơn số chỗ đang giữ.
- `{}` hoặc `maxParticipants: null` trả 400. Không nhận các cờ presence nội bộ từ client.
- Chỉ sửa trước khởi hành, kiểm tra guide ACTIVE/không trùng lịch và quyền branch như trước.
- Khóa lịch trước khi hợp nhất các trường để tránh ghi đè dữ liệu từ request đồng thời.
- HTTP 200, `ApiResponse<ScheduleResponse>`; không đổi field JSON.

`PUT` cũ giữ contract cũ: maxParticipants bắt buộc; guideId null/bị bỏ qua đều gỡ phân công.
Frontend hiện gửi cả guideId (có thể null) và maxParticipants qua PATCH nên giữ ý nghĩa form cũ.

### Điểm danh

`PATCH /api/participants/{id}/attendance`:

```json
{"status":"CHECKED_IN"}
```

Hoặc `{"status":"NO_SHOW"}`. Body/status bắt buộc; enum khác trả 400. Response HTTP 200,
`ApiResponse<ParticipantResponse>`. Đây là enum request, không thêm giá trị enum schema.
CHECKED_IN dùng checkInStatus; NO_SHOW dùng participantStatus theo nghiệp vụ hiện có.
Không nhận trạng thái thanh toán/hoàn tất/hủy qua endpoint attendance.

### Xác nhận thanh toán thủ công

`POST /api/participants/{id}/manual-payment-confirmations`:

```json
{"registrationReference":"UUID của lần đăng ký hiện tại","paymentReference":"BANK-RECEIPT-001"}
```

HTTP 200 trả trạng thái vé, gồm cả lần gọi lặp cùng UUID/chứng từ. Không tạo ID biên nhận mới.
Giữ kiểm tra thời hạn, UUID, chứng từ và chặn vé có giao dịch Payment; không gọi provider.
Contract backend `ParticipantPaymentService.confirm` và authority SCHEDULE_PAYMENT giữ nguyên,
không mở endpoint HTTP cho callback nội bộ này.

### Vé của tôi và lọc theo người dùng

`GET /api/participants/me?page=0&size=20` không cần userId. Backend dùng JWT;
userId truyền thêm không thể đổi chủ thể. GET `/api/participants` bắt buộc query userId,
thiếu trả 400; không cung cấp danh sách vé toàn hệ thống mặc định.
Tất cả danh sách giữ `page >= 0`, `size` từ 1 tới 100, mặc định page=0/size=50;
frontend gửi size=20. STAFF được lọc quyền trong SQL trước khi phân trang.

## Deprecation và triển khai

- OpenAPI `/v3/api-docs` đánh dấu **chỉ 11 operation legacy** bằng `deprecated: true`,
  mô tả route thay thế; operation canonical vẫn được công bố bình thường.
- Không phát hành ngày Sunset hay xóa route trong đợt này. HTTP method/status/body cũ giữ nguyên.
- Triển khai backend có alias trước, sau đó triển khai frontend dùng canonical routes.
  Frontend mới cần backend mới; rollback frontend có thể dùng lại route legacy trên backend mới.
- Chỉ xem xét xóa legacy trong một thay đổi breaking riêng sau khi kiểm kê mọi consumer,
  owner FE/Payment xác nhận chuyển đổi và nhóm thống nhất lịch thông báo. Không suy ra
  không còn consumer chỉ vì repo frontend hiện tại đã chuyển.
- Không thay route Payment, logic callback/refund, schema, DB chung, cấu hình bảo mật module khác.

## Frontend đã chuyển

Repo `travel-with-me-Frontend`: chỉ sửa `src/features/schedules/api.ts`, `api.test.ts`,
`CustomerPages.tsx`. Client dùng canonical endpoints cho đăng ký, cập nhật lịch,
assignment, vé cá nhân, điểm danh, xác nhận thủ công, lịch sử và review.
MyTickets lấy danh sách từ JWT thay vì gửi userId; component có key theo tài khoản
để reset dữ liệu khi đổi tài khoản. README đang có thay đổi của thành viên khác được giữ nguyên.

## Kiểm chứng

- Compile Java/test: PASS.
- Kiểm tra tập trung trước OpenAPI: 37 tests PASS (31 Schedule + 6 Payment bridge), 0 skipped.
- FE: `npm run test:module5` 20 tests PASS; `npm run lint` và `npm run build` PASS.
  Build có cảnh báo bundle > 500 kB hiện hữu.
- Hồi quy cuối có test OpenAPI: **181 tests PASS** (32 Schedule, 59 Payment),
  0 failures/errors/skipped, trên PostgreSQL 17.11 riêng `module5_api_final_20261028`.
  Flyway áp dụng và validate đủ 9 migrations, validate-on-migrate=true, Hibernate ddl-auto=validate.
- Test mới bao phủ contract mới/cũ, quyền anonymous/role/branch/guide/owner, PATCH absent/null,
  query theo JWT, validation, audit/idempotence và đăng ký đồng thời hai route cho chỗ cuối.
- MockMvc chạy filter/security/service/database thật trong test; API client FE dùng fetch giả
  để kiểm tra method/path/body. Chưa kiểm thử trình duyệt hoặc provider Payment thật.

Lệnh BE (database test riêng đã được tạo trên localhost):

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55485/module5_api_final_20261028'
$env:DB_USERNAME = 'module5_test'
$env:DB_PASSWORD = ''
$env:FLYWAY_ENABLED = 'true'
$env:SPRING_FLYWAY_VALIDATE_ON_MIGRATE = 'true'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1'
.\gradlew.bat compileJava compileTestJava test --offline --console=plain
```

Kết quả từng mốc: [MODULE_INTEGRATION_STATUS.md](MODULE_INTEGRATION_STATUS.md).
Lịch sử chuẩn hóa cấu trúc: [MODULE_5_STRUCTURE_STANDARDIZATION.md](MODULE_5_STRUCTURE_STANDARDIZATION.md).
