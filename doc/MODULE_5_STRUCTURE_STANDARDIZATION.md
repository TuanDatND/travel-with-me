# Module 5 — Baseline và chuẩn hóa cấu trúc

Ngày: 2026-10-28. Phạm vi mốc 1–3; giữ endpoint, JSON, schema và nghiệp vụ.

## Baseline trước refactor

- Working tree sạch trước khi thực hiện.
- `compileJava compileTestJava --offline --console=plain`: PASS (compileJava UP-TO-DATE, compileTestJava thực thi).
- Baseline runtime: ScheduleIntegrationTests 24 tests + ParticipantPaymentBridgeTests 6 tests PASS, 0 skipped; PostgreSQL 17.11 riêng module5_structure_20261028, Flyway validation và Hibernate validate bật.
- Payment hiện có `ParticipantPaymentBridgeImpl.confirm` gọi `ParticipantPaymentService.confirm`; chưa xác nhận provider thật/end-to-end.

## Mapping HTTP được giữ nguyên

```java
// ParticipantController.java
@PostMapping("/api/schedules/{scheduleId}/register")
@GetMapping("/api/schedules/{scheduleId}/participants")
@GetMapping("/api/participants/{id}")
@GetMapping("/api/participants/by-user/{userId}")
@PostMapping("/api/participants/{id}/cancel")
@PatchMapping("/api/participants/{id}/confirm-payment")
@PatchMapping("/api/participants/{id}/check-in")
@PatchMapping("/api/participants/{id}/no-show")
@GetMapping("/api/participants/{id}/histories")
@GetMapping("/api/participants/payment-reviews")
// ScheduleController.java
@RequestMapping("/api/schedules")
@PostMapping
@GetMapping
@GetMapping("/my-assignments")
@GetMapping("/my-assignments/{id}")
@GetMapping("/{id}")
@PutMapping("/{id}")
@PostMapping("/{id}/reschedule")
@PatchMapping("/{id}/status")
@GetMapping("/{id}/histories")
```

## DTO baseline (giữ nguyên record components/JSON)

```java
public record CancelParticipantRequest(@Size(max = 2000) String reason) {
public record ConfirmParticipantRequest(@NotNull UUID registrationReference,
        @NotBlank @Size(max = 180) String paymentReference) {
public record CreateScheduleRequest(
        @NotNull(message = "eventId is required")
        Long eventId,

        @NotNull(message = "branchId is required")
        Long branchId,

        Long guideId,

        @NotNull(message = "startDatetime is required")
        OffsetDateTime startDatetime,

        @NotNull(message = "endDatetime is required")
        @Future(message = "endDatetime must be in the future")
        OffsetDateTime endDatetime,

        @NotNull(message = "maxParticipants is required")
        @Min(value = 1, message = "maxParticipants must be at least 1")
        Integer maxParticipants,

        OffsetDateTime registrationClosesAt
) {
public record GuideScheduleResponse(
        Long id, Long eventId, String eventName, Long branchId, String branchName, Long guideId,
        OffsetDateTime startDatetime, OffsetDateTime endDatetime, Integer maxParticipants,
        Integer currentParticipants, ScheduleStatus status, OffsetDateTime createdAt,
        OffsetDateTime registrationClosesAt) {
public record ParticipantHistoryResponse(Long id, UUID registrationReference, String action,
        String oldStatus, String newStatus, java.math.BigDecimal registeredPrice, Long changedBy, String reason, OffsetDateTime changedAt) {
public record ParticipantResponse(
        Long id,
        Long scheduleId,
        Long userId,
        OffsetDateTime registrationDate,
        ParticipantStatus participantStatus,
        CheckInStatus checkInStatus,
        BigDecimal registeredPrice,
        String note,
        java.util.UUID registrationReference,
        OffsetDateTime expiresAt
) {
public record PaymentReviewResponse(Long id, Long participantId, UUID registrationReference,
        Long transactionId, String kind, java.math.BigDecimal amount, String reason, OffsetDateTime createdAt) {
public record RegisterParticipantRequest(
        @Size(max = 2000, message = "note tối đa 2000 ký tự") String note) {
public record RescheduleRequest(
        @NotNull(message = "newStartDatetime is required")
        OffsetDateTime newStartDatetime,

        @NotNull(message = "newEndDatetime is required")
        OffsetDateTime newEndDatetime,

        @NotBlank(message = "changeReason must not be blank")
        @jakarta.validation.constraints.Size(max = 2000) String changeReason,

        OffsetDateTime registrationClosesAt
) {
public record ScheduleHistoryResponse(
        Long id,
        Long scheduleId,
        OffsetDateTime oldStartDatetime,
        OffsetDateTime newStartDatetime,
        OffsetDateTime oldEndDatetime,
        OffsetDateTime newEndDatetime,
        String oldStatus,
        String newStatus,
        String changeReason,
        OffsetDateTime changedAt,
        Long changedBy
) {
public record ScheduleResponse(
        Long id,
        Long eventId,
        Long branchId,
        Long guideId,
        OffsetDateTime startDatetime,
        OffsetDateTime endDatetime,
        Integer maxParticipants,
        Integer currentParticipants,
        ScheduleStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime registrationClosesAt
) {
public record UpdateScheduleRequest(
        Long guideId,

        @NotNull(message = "maxParticipants is required")
        @Min(value = 1, message = "maxParticipants must be at least 1")
        Integer maxParticipants
) {
public record UpdateScheduleStatusRequest(
        @NotNull(message = "status is required")
        ScheduleStatus status,

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 2000) String changeReason
) {
```

## Ràng buộc tích hợp

- Bảng: event_schedules, event_schedule_history, event_participants, schedule_participant_history, schedule_payment_reviews. Không sửa migration, PK/FK, enum values hoặc JPA mappings.
- Public IDs: schedule_id, participant_id Long; registrationReference UUID; eventId/branchId/guideId/userId theo contract hiện tại.
- Giữ thứ tự khóa schedule → participant → transaction, transaction annotations, cutoff, expiry, chống lặp và authority SCHEDULE_PAYMENT.
- Payment import entity/enums/repository Schedule; chỉ cập nhật import enum bắt buộc khi chuyển package.
- Giữ EventService/GuideService và ScheduleIntegrationRepository hiện có; chưa thiết kế lại đầu nối liên module.
- ScheduleSettings giữ constructor có @Value và Clock để bảo toàn validation/configuration.

## Kết quả theo mốc

- Mốc 1: baseline source và compile đã ghi nhận; 30 tests runtime PASS (24 Schedule + 6 Payment bridge), 0 skipped.

- Mốc 2: chuyển 21 types và test về đúng package; constructor injection Lombok, getter/noarg entity, explicit imports. Compile PASS; 30 tests runtime PASS, 0 skipped, PostgreSQL riêng/Flyway/Hibernate validation.

## Cấu trúc và quy tắc tên đã áp dụng

- Package chữ thường, theo trách nhiệm: controller, dto.request, dto.response, entity, enums, exception, repository, security, scheduler, service, service.impl, validator, util, config.
- Mỗi public type một file PascalCase trùng tên. Request: động từ nghiệp vụ + đối tượng + Request; response: đối tượng/kết quả + Response.
- Service nghiệp vụ công khai là interface; implementation ở service.impl. Component nội bộ hoặc utility thuần không tạo interface hình thức.
- Dependency final có tên camelCase theo type đầy đủ (eventScheduleRepository, participantPaymentService, scheduleSettings); Lombok RequiredArgsConstructor giữ thứ tự dependency cũ.
- Entity dùng Getter/NoArgsConstructor(PROTECTED), giữ constructor và method nghiệp vụ; không thêm setter/Data.
- Import tường minh; tên enum đầy đủ trong chuỗi JPQL là cần thiết vì Java import không định nghĩa namespace của HQL.
- Giữ entity EventSchedule/EventParticipant, tên method API/service, ConfirmationResult lồng trong ParticipantPaymentService và ScheduleSettings constructors để bảo toàn đầu nối.
- Giữ ApiResponse, mảng data, page/size, HTTP methods/routes, JSON components/validation và mã trạng thái hiện có. Việc chuẩn hóa tên API ở mốc 4 chưa triển khai.

| Trước | Sau |
| --- | --- |
| dto/*Request | dto.request/*Request |
| dto/*Response | dto.response/*Response |
| dto/ConfirmParticipantRequest | dto.request/ConfirmParticipantPaymentRequest |
| dto/PaymentReviewResponse | dto.response/SchedulePaymentReviewResponse |
| entity/ScheduleStatus, ParticipantStatus, CheckInStatus | enums (giữ tên/value) |
| controller/ScheduleExceptionHandler | exception/ScheduleExceptionHandler |
| service/ParticipantLifecycle | service.impl/ParticipantLifecycle |
| service/ScheduleLifecycle | validator/ScheduleLifecycleRules |
| service/SchedulePages | util/SchedulePageRequests |
| config/ScheduleJobs | scheduler/ScheduleExpiryJob |
| test .../sba_treektour/schedule/ScheduleIntegrationTests | test .../sba_treektour/modules/schedule/ScheduleIntegrationTests |

Ngoài module 5 chỉ đổi import enum trong ba implementation Payment (ParticipantPaymentBridgeImpl,
TransactionsServiceImpl, RefundServiceImpl) và hai test Payment (ParticipantPaymentBridgeTests,
RefundServiceTests). Không sửa logic Payment, frontend, configuration dùng chung hoặc migration.

## Kiểm tra sau đổi tên

- Đối chiếu 52 Java types với baseline sau chuẩn hóa namespace/type/dependency và boilerplate Lombok: cùng token nghiệp vụ; đây là kiểm tra source, không phải chứng minh toàn bộ runtime.
- 9 migration giữ nguyên SHA-256; DTO record components, HTTP mapping, validation, SQL, security và transaction annotations được giữ nguyên (JPQL chỉ cập nhật namespace enum).
- Mốc 3: compileJava + compileTestJava PASS; toàn bộ **173 tests PASS**, 0 failures/errors/skipped, gồm 24 Schedule và 59 Payment, trên PostgreSQL 17.11 database sạch module5_structure_final_20261028. Flyway áp dụng thành công và validate đủ 9 migrations; Hibernate validate bật.
- Flyway bật và validate-on-migrate=true; Hibernate ddl-auto=validate. Mail test trỏ localhost:1; không gọi DB chung/provider thật.


Lệnh kiểm tra cuối (PowerShell; PostgreSQL test localhost đã chạy và database mới đã tạo):

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55485/module5_structure_final_20261028'
$env:DB_USERNAME = 'module5_test'
$env:DB_PASSWORD = ''
$env:FLYWAY_ENABLED = 'true'
$env:SPRING_FLYWAY_VALIDATE_ON_MIGRATE = 'true'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1'
.\gradlew.bat compileJava compileTestJava test --offline --console=plain
```

Báo cáo runtime: `build/reports/tests/test/index.html` (git ignored). Test thực thi mới,
không lấy trạng thái UP-TO-DATE làm bằng chứng test runtime. Các cảnh báo biên dịch
hiện có ở ImageValidatorTests/OrderPaymentTests thuộc module khác được giữ nguyên.
Không thay cấu hình production, không áp dụng migration hoặc ghi dữ liệu DB chung.
