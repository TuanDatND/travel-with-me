# Tích hợp tour/guide với lịch và đăng ký

Ngày: 2026-10-10. Nhánh: `feature/tour-schedule-integration`.

## Phạm vi

Người dùng đã cho phép sửa các điểm nối trong module member 5. Tái sử dụng EventService/GuideService của member 4, JWT của member 1 và nghiệp vụ giữ chỗ của member 5. Không sửa Account, Branch, Order, Inventory, Payment, cấu hình môi trường hoặc migration.

- Tour phải PUBLISHED khi tạo lịch, đổi guide/ngày, mở lịch hoặc đăng ký.
- Branch phải tồn tại và ACTIVE khi tạo lịch (chỉ đọc database).
- Guide có thể để `null` khi chưa phân công. Nếu có guideId, hồ sơ và tài khoản phải ACTIVE.
- Tạo/dời lịch phải có ngày khởi hành tương lai; lịch đã khởi hành, IN_PROGRESS, COMPLETED hoặc CANCELLED không được đổi ngày/phân công.
- Trùng lịch kiểm tra theo **userId**, kể cả hai guideId khác nhau của cùng người. Hai khoảng thời gian chạm đầu/cuối được phép. CANCELLED/COMPLETED không giữ thời gian guide; CLOSED vẫn giữ.
- Khóa giao dịch PostgreSQL theo người khi phân công; khóa lịch trước khi thay đổi vé để tránh bán vượt chỗ, đăng ký trùng, hủy hai lần làm giảm chỗ của người khác.
- Vé mới PENDING và NOT_CHECKED_IN. Chỉ vé CANCELLED được đăng ký lại, dùng lại participant ID và chốt giá hiện tại.

## Quyền truy cập

| API | Quyền |
| --- | --- |
| GET `/api/events`, `/api/guides` | Công khai theo điều kiện module tour |
| GET `/api/schedules`, `/api/schedules/{id}` | Khách/CUSTOMER/GUIDE chỉ xem lịch của tour PUBLISHED; ADMIN/STAFF xem mọi trạng thái tour |
| POST `/api/schedules`, PUT `/{id}`, POST `/{id}/reschedule`, PATCH `/{id}/status`, GET `/{id}/histories` | ADMIN/STAFF |
| POST `/api/schedules/{id}/register` | Tài khoản ACTIVE đã đăng nhập; luôn đăng ký cho chính principal JWT |
| GET `/api/participants/{id}`, GET `/api/participants/by-user/{userId}`, POST `/api/participants/{id}/cancel` | Chủ vé/tài khoản được truy vấn hoặc ADMIN/STAFF |
| GET `/api/schedules/{id}/participants`, PATCH `/api/participants/{id}/check-in`, PATCH `/{id}/no-show` | ADMIN/STAFF hoặc guide ACTIVE được phân công, tài khoản ACTIVE |
| PATCH `/api/participants/{id}/confirm-payment` | ADMIN/STAFF; xác nhận thủ công theo nghiệp vụ sẵn có |

Bộ lọc lịch `eventId`, `branchId`, `status` được kết hợp cùng lúc. Ví dụ `eventId=10&status=OPEN` chỉ trả lịch OPEN của tour 10. Muốn khách chỉ thấy lịch có thể đặt, frontend dùng `status=OPEN` và ngày khởi hành; backend vẫn kiểm tra lại khi đăng ký.

## Luồng demo và output

Các ID/thời gian dưới đây là **ví dụ**, thay bằng ID có thật và ngày tương lai. Không phải dữ liệu đã tạo trên Supabase.

### 1. Chuẩn bị tour và guide

ADMIN tạo nội dung tour, upload ảnh rồi chuyển tour sang PUBLISHED theo API hiện có. Dùng `eventId` trả về. Guide lấy `guideId` từ hồ sơ ACTIVE của một tài khoản ACTIVE. Dùng `branchId` của chi nhánh ACTIVE.

### 2. ADMIN tạo lịch

`POST /api/schedules`, dùng JWT ADMIN hoặc STAFF:

```json
{
  "eventId": 10,
  "branchId": 1,
  "guideId": 3,
  "startDatetime": "2026-11-20T07:00:00+07:00",
  "endDatetime": "2026-11-20T17:00:00+07:00",
  "maxParticipants": 10
}
```

HTTP **201**; response theo mẫu:

```json
{
  "status": 201,
  "message": "Tạo lịch tour thành công",
  "data": {
    "id": 20,
    "eventId": 10,
    "branchId": 1,
    "guideId": 3,
    "startDatetime": "2026-11-20T07:00:00+07:00",
    "endDatetime": "2026-11-20T17:00:00+07:00",
    "maxParticipants": 10,
    "currentParticipants": 0,
    "status": "OPEN",
    "createdAt": "2026-10-10T14:00:00+07:00"
  },
  "timestamp": "2026-10-10T14:00:00+07:00"
}
```

`data.id` là **scheduleId**, không phải eventId. Một tour có nhiều lịch. `maxParticipants - currentParticipants` là số chỗ còn; trạng thái FULL xuất hiện khi hết chỗ. Cách hiển thị múi giờ trong JSON có thể khác ví dụ nhưng cùng thời điểm.

### 3. Khách xem lịch không cần đăng nhập

`GET /api/schedules?eventId=10&status=OPEN` → HTTP **200**, `data` là **mảng ScheduleResponse**, không phải PageResponse của danh sách tour. `GET /api/schedules/20` → `data` là một lịch.

### 4. Đăng nhập, giữ chỗ

Đăng nhập `/api/auth/login`, lấy `data.token`, điền vào Authorize của Swagger. Gọi `POST /api/schedules/20/register`:

```json
{
  "note": "Ăn chay, cần tư vấn đồ trekking"
}
```

Có thể dùng `{}` nếu không ghi chú. Contract **đã đổi**: không gửi `userId` hoặc `registeredPrice`. Hai trường cũ không còn trong DTO/Swagger; nếu client cũ gửi dư, chúng không điều khiển danh tính hay giá. `note` tối đa 2000 ký tự.

Giả sử principal JWT là user 7 và giá tour là 1500000, HTTP **201**:

```json
{
  "status": 201,
  "message": "Đăng ký giữ chỗ tour thành công",
  "data": {
    "id": 30,
    "scheduleId": 20,
    "userId": 7,
    "registrationDate": "2026-10-10T14:05:00+07:00",
    "participantStatus": "PENDING",
    "checkInStatus": "NOT_CHECKED_IN",
    "registeredPrice": 1500000,
    "note": "Ăn chay, cần tư vấn đồ trekking"
  },
  "timestamp": "2026-10-10T14:05:00+07:00"
}
```

- `data.id` là **participantId**, dùng để đọc/hủy vé, xác nhận hoặc điểm danh.
- `userId` lấy từ JWT, khách không đăng ký thay người khác bằng request này.
- `registeredPrice` chốt từ `tour.basePrice` lúc đăng ký. Sửa giá tour sau đó không sửa giá vé cũ. Hủy rồi đăng ký lại chốt giá mới.
- PENDING là giữ chỗ và **đã chiếm một chỗ**, chưa xác nhận thanh toán. Không gọi confirm-payment để giả lập việc thanh toán thật.

### 5. Đọc/hủy vé và kiểm tra sức chứa

- Chủ vé: `GET /api/participants/30`, hoặc `GET /api/participants/by-user/7`.
- Chủ vé: `POST /api/participants/30/cancel` → HTTP 200, `participantStatus=CANCELLED`, giải phóng một chỗ. Hủy lại trả 200 và không trừ thêm.
- Guide được phân công: `GET /api/schedules/20/participants`.
- Xác nhận thủ công dành ADMIN/STAFF: `PATCH /api/participants/30/confirm-payment` với vé PENDING → CONFIRMED. Guide được phân công có thể check-in vé CONFIRMED. Lịch COMPLETED sẽ hoàn tất các vé đã check-in theo nghiệp vụ sẵn có.

## HTTP lỗi cần biết

| Trường hợp | HTTP |
| --- | --- |
| Chưa đăng nhập hoặc JWT tài khoản bị khóa/ngừng hoạt động khi gọi API cần đăng nhập | 401 |
| CUSTOMER tạo lịch, đọc/hủy vé người khác, tự xác nhận thanh toán | 403 |
| Guide không được phân công xem danh sách hoặc điểm danh | 403 |
| Tour/guide/schedule không tồn tại; khách đọc lịch của tour không PUBLISHED | 404 |
| Tour chưa PUBLISHED, guide/tài khoản không ACTIVE, trùng lịch guide | 409 |
| Đăng ký trùng, hết chỗ/lịch không OPEN, đã khởi hành, tour đã lưu trữ | 409 |
| Chi nhánh không ACTIVE/không tồn tại, thời gian sai, ghi chú quá dài, thao tác trạng thái không phù hợp | 400 |

Success và lỗi đều dùng ApiResponse hiện có. Lỗi tích hợp dùng `data.code`/`fieldErrors`; lỗi nghiệp vụ sẵn có của schedule giữ `data=null` và thông báo trong `message`. Không đổi toàn bộ format lỗi của member 5 trong lần nối này.

## Kiểm thử

**Kết quả: compileJava và toàn bộ 27 tests PASS**, gồm 8 tests tích hợp mới, 19 tests hiện có.

`TourScheduleIntegrationTests` dùng PostgreSQL local riêng, controller/service thật và login/JWT thật; không mock tour, guide, lịch hay vé. Fixture tour PUBLISHED được chèn SQL để tập trung kiểm tra điểm nối; việc tạo/công bố tour qua API được kiểm tra bởi bộ test tour hiện có.

Chạy trên database test riêng đã tạo (không dùng database chung của nhóm):

```bash
DB_URL=jdbc:postgresql://127.0.0.1:55439/tour_schedule_integration \
DB_USERNAME=tour_test DB_PASSWORD= FLYWAY_ENABLED=true \
bash gradlew compileJava test
```

Database local riêng phải chạy và `.env` local phải có đủ biến để ứng dụng khởi động. Database rỗng dùng migration hiện có, không cần tắt Flyway. Cổng 55439 chỉ là cổng cụm PostgreSQL test local của máy đang làm việc.

Chưa nối callback ZaloPay với participant. Không thay đổi API payment của member 6. Bộ test hiện tại không xác nhận thanh toán thật hoặc upload Cloudinary thật. Cần restart backend đang chạy để Swagger dùng code mới; không tự thay process IntelliJ hoặc dữ liệu Supabase của nhóm.
