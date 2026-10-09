# Tài liệu kỹ thuật Module 5: Lịch & Đăng ký tham gia (Schedule & Participant)

> **Người phụ trách:** Member 5  
> **Package:** `com.coc.sba_treektour.schedule`  
> **Trạng thái:** `IN_PROGRESS` (Sẵn sàng ID contract cho Module 6 Payment)  
> **Ngày cập nhật:** 2026-10-09  

---

## 1. Tổng quan & Phạm vi

Module 5 chịu trách nhiệm về toàn bộ vòng đời tổ chức lịch trình của các tour trekking, cắm trại dã ngoại và quản lý việc đăng ký, giữ chỗ, thanh toán, điểm danh (check-in) của người tham gia.

### Các bảng dữ liệu sở hữu:
1. **`event_schedules`**: Lịch tổ chức cụ thể của một tour tại một chi nhánh.
2. **`event_schedule_history`**: Nhật ký lịch sử thay đổi ngày giờ tổ chức hoặc thay đổi trạng thái của lịch.
3. **`event_participants`**: Danh sách người đăng ký tham gia tour trekking.

---

## 2. Mô hình Thực thể & Dữ liệu (Entities & Enums)

### 2.1. Các Enums nghiệp vụ
- **`ScheduleStatus`**:
  - `OPEN`: Đang mở đăng ký cho khách hàng.
  - `FULL`: Đã đủ số người tối đa (`current_participants >= max_participants`).
  - `CLOSED`: Chốt sổ trước ngày khởi hành (mua bảo hiểm, xin phép kiểm lâm).
  - `IN_PROGRESS`: Tour đang diễn ra.
  - `COMPLETED`: Tour đã kết thúc thành công.
  - `CANCELLED`: Tour bị hủy (do thời tiết xấu hoặc không đủ số lượng tối thiểu).
- **`ParticipantStatus`**:
  - `PENDING`: Vừa đặt chỗ, đang chờ thanh toán (thời hạn giữ slot).
  - `CONFIRMED`: Đã thanh toán thành công (hoặc xác nhận thủ công).
  - `COMPLETED`: Đã tham gia hoàn thành tour.
  - `CANCELLED`: Đã hủy đăng ký (giải phóng slot).
  - `NO_SHOW`: Không đến điểm tập trung vào ngày khởi hành.
- **`CheckInStatus`**:
  - `NOT_CHECKED_IN`: Chưa điểm danh.
  - `CHECKED_IN`: Đã có mặt tại điểm tập trung và điểm danh thành công.

### 2.2. Chi tiết Entity & Ánh xạ CSDL

#### `EventSchedule` (bảng `event_schedules`)
| Thuộc tính Java | Cột CSDL | Kiểu dữ liệu | Ghi chú |
|---|---|---|---|
| `id` | `schedule_id` | `Long` (PK) | Identity ID |
| `eventId` | `event_id` | `Long` | Khóa ngoại sang Tour (Module 4) |
| `branchId` | `branch_id` | `Long` | Khóa ngoại sang Chi nhánh (Module 1) |
| `guideId` | `guide_id` | `Long` (nullable) | Khóa ngoại sang Hướng dẫn viên (Module 4) |
| `startDatetime` | `start_datetime` | `OffsetDateTime` | Thời điểm bắt đầu |
| `endDatetime` | `end_datetime` | `OffsetDateTime` | Thời điểm kết thúc (`end > start`) |
| `maxParticipants` | `max_participants` | `Integer` | Số người tối đa (`> 0`) |
| `currentParticipants` | `current_participants` | `Integer` | Số người đã giữ chỗ/đăng ký |
| `status` | `status` | `ScheduleStatus` | Mặc định `OPEN` |
| `createdAt` | `created_at` | `OffsetDateTime` | Thời điểm tạo lịch |

#### `EventScheduleHistory` (bảng `event_schedule_history`)
| Thuộc tính Java | Cột CSDL | Kiểu dữ liệu | Ghi chú |
|---|---|---|---|
| `id` | `history_id` | `Long` (PK) | Identity ID |
| `schedule` | `schedule_id` | `EventSchedule` | `@ManyToOne` nội bộ Module 5 |
| `oldStartDatetime` | `old_start_datetime` | `OffsetDateTime` | Ngày bắt đầu trước khi dời |
| `newStartDatetime` | `new_start_datetime` | `OffsetDateTime` | Ngày bắt đầu sau khi dời |
| `oldEndDatetime` | `old_end_datetime` | `OffsetDateTime` | Ngày kết thúc trước khi dời |
| `newEndDatetime` | `new_end_datetime` | `OffsetDateTime` | Ngày kết thúc sau khi dời |
| `oldStatus` | `old_status` | `String` | Trạng thái trước khi đổi |
| `newStatus` | `new_status` | `String` | Trạng thái sau khi đổi |
| `changeReason` | `change_reason` | `String` | Lý do dời lịch / hủy lịch |
| `changedAt` | `changed_at` | `OffsetDateTime` | Thời điểm ghi nhận |

#### `EventParticipant` (bảng `event_participants`)
| Thuộc tính Java | Cột CSDL | Kiểu dữ liệu | Ghi chú |
|---|---|---|---|
| `id` | `participant_id` | `Long` (PK) | Identity ID (Dùng cho Module 6 thanh toán) |
| `schedule` | `schedule_id` | `EventSchedule` | `@ManyToOne` nội bộ Module 5 |
| `userId` | `user_id` | `Long` | Khóa ngoại sang Tài khoản (Module 1) |
| `registrationDate` | `registration_date` | `OffsetDateTime` | Ngày đăng ký |
| `participantStatus` | `participant_status` | `ParticipantStatus` | `PENDING`, `CONFIRMED`, `CANCELLED`... |
| `checkInStatus` | `check_in_status` | `CheckInStatus` | `NOT_CHECKED_IN`, `CHECKED_IN` |
| `registeredPrice` | `registered_price` | `BigDecimal` | Giá vé snapshot tại thời điểm đặt |
| `note` | `note` | `String` | Sức khỏe, dị ứng, liên hệ khẩn cấp |

> **Ràng buộc:** `UNIQUE (schedule_id, user_id)` — Mỗi user chỉ có tối đa 1 dòng cho mỗi lịch. Hỗ trợ tái đăng ký (Re-registration) trên chính dòng này.

---

## 3. Cơ chế kỹ thuật nổi bật

### 3.1. Atomic Update Capacity kết hợp `@Transactional`
Để tránh race condition khi nhiều khách hàng cùng bấm đặt chỗ những slot cuối cùng:
- **Tăng slot:** Thực hiện câu truy vấn Atomic:
  ```sql
  UPDATE event_schedules
  SET current_participants = current_participants + 1,
      status = CASE WHEN current_participants + 1 >= max_participants THEN 'FULL' ELSE status END
  WHERE schedule_id = ? AND status = 'OPEN' AND current_participants < max_participants
  ```
  Nếu kết quả trả về `0 rows affected` $\rightarrow$ Ném ngoại lệ `ScheduleNotAvailableException` (Lịch không mở hoặc đã hết chỗ).
- **Giảm slot (khi hủy vé):**
  ```sql
  UPDATE event_schedules
  SET current_participants = current_participants - 1,
      status = CASE WHEN status = 'FULL' THEN 'OPEN' ELSE status END
  WHERE schedule_id = ? AND current_participants > 0
  ```
  Tự động mở lại trạng thái `OPEN` nếu lịch đang ở trạng thái `FULL`.

### 3.2. Xử lý Tái đăng ký (Re-registration)
Theo ràng buộc CSDL `uq_event_participants_schedule_user`:
- Khi người dùng đăng ký:
  - Nếu đã có vé và trạng thái là `PENDING` hoặc `CONFIRMED` $\rightarrow$ Ném lỗi `DuplicateRegistrationException`.
  - Nếu đã từng đăng ký nhưng bị hủy (`CANCELLED`) hoặc `NO_SHOW` $\rightarrow$ Tái sử dụng dòng cũ, cập nhật trạng thái mới về `PENDING`, reset `check_in_status` về `NOT_CHECKED_IN`, snapshot lại giá vé và thời gian đăng ký.

### 3.3. Tự động ghi vết Lịch sử Dời lịch (Reschedule History)
- Bất kỳ thao tác dời ngày (`/api/schedules/{id}/reschedule`) hoặc thay đổi trạng thái (`/api/schedules/{id}/status`) đều tự động ghi một bản ghi vào `event_schedule_history` kèm lý do.

---

## 4. Chuẩn hóa Định dạng JSON Phản hồi (Unified API Response)

Tất cả các API (thành công hoặc thất bại) đều được đóng gói theo định dạng chuẩn `ApiResponse<T>`:

```json
{
  "status": 200,
  "message": "Thành công",
  "data": { ... },
  "timestamp": "2026-10-09T21:40:00+07:00"
}
```

Khi có lỗi nghiệp vụ hoặc validation:
```json
{
  "status": 409,
  "message": "Tour schedule is not available for registration",
  "data": null,
  "timestamp": "2026-10-09T21:40:00+07:00"
}
```

---

## 5. Danh sách RESTful API Contract

### 5.1. Nhóm Quản lý Lịch Tour (`ScheduleController`)

#### 1. Tạo lịch mới
- **Endpoint:** `POST /api/schedules`
- **Request Body:**
  ```json
  {
    "eventId": 1,
    "branchId": 2,
    "guideId": 5,
    "startDatetime": "2026-11-15T06:00:00+07:00",
    "endDatetime": "2026-11-17T18:00:00+07:00",
    "maxParticipants": 20
  }
  ```
- **Response:** `201 Created` (`ApiResponse<ScheduleResponse>`)

#### 2. Lấy danh sách lịch (có lọc)
- **Endpoint:** `GET /api/schedules?eventId=1&branchId=2&status=OPEN`
- **Response:** `200 OK` (`ApiResponse<List<ScheduleResponse>>`)

#### 3. Xem chi tiết lịch
- **Endpoint:** `GET /api/schedules/{id}`
- **Response:** `200 OK` (`ApiResponse<ScheduleResponse>`)

#### 4. Cập nhật thông tin lịch
- **Endpoint:** `PUT /api/schedules/{id}`
- **Request Body:**
  ```json
  {
    "guideId": 6,
    "maxParticipants": 25
  }
  ```
- **Response:** `200 OK`

#### 5. Dời ngày tour (Reschedule)
- **Endpoint:** `POST /api/schedules/{id}/reschedule`
- **Request Body:**
  ```json
  {
    "newStartDatetime": "2026-11-20T06:00:00+07:00",
    "newEndDatetime": "2026-11-22T18:00:00+07:00",
    "changeReason": "Do ảnh hưởng áp thấp nhiệt đới tại khu vực trekking"
  }
  ```
- **Response:** `200 OK` (Tự động tạo bản ghi history)

#### 6. Đổi trạng thái lịch
- **Endpoint:** `PATCH /api/schedules/{id}/status`
- **Request Body:**
  ```json
  {
    "status": "CLOSED",
    "changeReason": "Chốt sổ trước ngày khởi hành để mua bảo hiểm"
  }
  ```
- **Response:** `200 OK`

#### 7. Lấy lịch sử biến động ngày giờ/trạng thái
- **Endpoint:** `GET /api/schedules/{id}/histories`
- **Response:** `200 OK` (Danh sách `ScheduleHistoryResponse`)

---

### 4.2. Nhóm Đăng ký & Điểm danh (`ParticipantController`)

#### 1. Đăng ký tham gia tour (Giữ chỗ)
- **Endpoint:** `POST /api/schedules/{scheduleId}/register`
- **Request Body:**
  ```json
  {
    "userId": 10,
    "registeredPrice": 2500000.00,
    "note": "Khách có tiền sử dị ứng tôm cua, liên hệ khẩn cấp: 0988123456"
  }
  ```
- **Response:** `201 Created` (Tạo vé `PENDING`, tăng `current_participants`)

#### 2. Lấy danh sách đoàn của một lịch tour
- **Endpoint:** `GET /api/schedules/{scheduleId}/participants`
- **Response:** `200 OK` (Dùng xuất danh sách bảo hiểm, kiểm lâm, điểm danh)

#### 3. Xem chi tiết 1 vé
- **Endpoint:** `GET /api/participants/{id}`
- **Response:** `200 OK`

#### 4. Lấy danh sách tour của một User
- **Endpoint:** `GET /api/participants/by-user/{userId}`
- **Response:** `200 OK`

#### 5. Hủy đăng ký
- **Endpoint:** `POST /api/participants/{id}/cancel`
- **Response:** `200 OK` (Cập nhật `CANCELLED`, giảm `current_participants`)

#### 6. Xác nhận thanh toán (Module 6 gọi sang)
- **Endpoint:** `PATCH /api/participants/{id}/confirm-payment`
- **Response:** `200 OK` (Chuyển sang `CONFIRMED`)

#### 7. Điểm danh khách tại điểm tập trung (Guide / Staff)
- **Endpoint:** `PATCH /api/participants/{id}/check-in`
- **Response:** `200 OK` (Chuyển `checkInStatus` sang `CHECKED_IN`)

#### 8. Đánh dấu vắng mặt (No-show)
- **Endpoint:** `PATCH /api/participants/{id}/no-show`
- **Response:** `200 OK` (Chuyển `participantStatus` sang `NO_SHOW`)

---

## 5. Xử lý Ngoại lệ (Exceptions & HTTP Codes)

Tất cả ngoại lệ được bắt tập trung tại `ScheduleExceptionHandler`:
- `ScheduleNotFoundException` $\rightarrow$ `404 NOT FOUND`
- `ParticipantNotFoundException` $\rightarrow$ `404 NOT FOUND`
- `ScheduleNotAvailableException` $\rightarrow$ `409 CONFLICT` (Lịch đã đầy chỗ hoặc chưa mở)
- `DuplicateRegistrationException` $\rightarrow$ `409 CONFLICT` (Người dùng đã có vé còn hiệu lực)
- `InvalidScheduleOperationException` $\rightarrow$ `400 BAD REQUEST` (Thời gian kết thúc trước bắt đầu, hủy vé đã hoàn thành, v.v.)

---

## 6. Hướng dẫn phối hợp với các Module khác

| Module liên quan | Trường kết nối | Trạng thái hiện tại | Hướng dẫn tích hợp |
|---|---|---|---|
| **Module 1 (Account)** | `user_id` | `NOT_STARTED` | Module 5 lưu `userId: Long`. Khi User `READY_TO_MAP`, chuyển sang `@ManyToOne User`. |
| **Module 1 (Branch)** | `branch_id` | `NOT_STARTED` | Module 5 lưu `branchId: Long`. |
| **Module 4 (Tour & Guide)** | `event_id`, `guide_id` | `NOT_STARTED` | Module 5 lưu `eventId: Long`, `guideId: Long`. |
| **Module 6 (Payment)** | `participant_id` | `NOT_STARTED` | Module 6 dùng `participant_id: Long` trong bảng `transactions` để tạo giao dịch thanh toán tour. Khi thanh toán thành công, Module 6 gọi `PATCH /api/participants/{id}/confirm-payment`. |
