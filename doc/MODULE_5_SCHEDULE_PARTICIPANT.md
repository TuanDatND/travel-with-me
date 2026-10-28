# Module 5 — Lịch và đăng ký tham gia

Cập nhật: 2026-10-28 (cấu trúc mốc 1–3 và API mốc 4–5). Package: `com.coc.sba_treektour.modules.schedule`.
Tài liệu này thay thế contract lịch/vé trong các tài liệu demo ngày 2026-10-10.
API canonical mới, alias legacy, request PATCH/attendance và lộ trình deprecation:
[MODULE_5_API_MIGRATION.md](MODULE_5_API_MIGRATION.md). Bảng route bên dưới giữ các route
legacy vẫn được hỗ trợ; quyền tương ứng áp dụng cả route canonical trong tài liệu chuyển đổi.
Phân tích trước khi sửa: [MODULE_5_ARCHITECTURE_REVIEW.md](MODULE_5_ARCHITECTURE_REVIEW.md).

## Kiến trúc và phạm vi

```text
controller → service interface → service/impl → repository → PostgreSQL
                             ↘ security policy / lifecycle rules / entity
```

- `ScheduleService`, `ParticipantService`: interface API nghiệp vụ, controller inject interface.
- `service/impl`: transaction, điều phối và kiểm tra quyền trên tài nguyên.
- `security/ScheduleAccessPolicy`: principal JWT, chủ vé, chi nhánh và guide được phân công.
- `config/ScheduleSecurityConfig`: filter chain riêng, Order(0), chỉ nhận đường dẫn lịch/vé.
- Repository module 5 đều là Spring Data interface (`JpaRepository` cho entity CRUD; `Repository` cho query chuyên biệt).
- `repository/ScheduleIntegrationRepository`: native `@Query` đọc Account/Branch/Payment và advisory transaction lock guide; không dùng JdbcTemplate.
- `repository/GuideScheduleRepository`: native query với interface projection và Pageable; service chuyển projection thành DTO.
- Projection native đọc timestamptz dưới dạng Instant; DTO lịch chuyển về OffsetDateTime UTC, so sánh Payment theo instant để giữ đúng thời điểm.
- `service/impl/ParticipantLifecycle`: thao tác nội bộ bắt buộc có transaction và đã khóa lịch.
- `dto/request`, `dto/response`: request và response tách riêng; `enums` chứa trạng thái.
- `validator/ScheduleLifecycleRules`: quy tắc vòng đời thuần; `util/SchedulePageRequests`: kiểm tra phân trang.
- `scheduler/ScheduleExpiryJob`: worker, giữ cấu hình và lịch chạy hiện có.
- Constructor injection dùng `@RequiredArgsConstructor`; `ScheduleSettings` giữ constructor cấu hình `@Value` và Clock để bảo toàn validation/testability.
- Baseline, quy tắc đặt tên và đối chiếu contract: [MODULE_5_STRUCTURE_STANDARDIZATION.md](MODULE_5_STRUCTURE_STANDARDIZATION.md).
- `HoldExpiryService`: worker nội bộ; `ParticipantPaymentService`: contract Payment nội bộ.
- `exception`: lỗi nghiệp vụ độc lập HTTP; controller advice chuyển thành ApiResponse.

Giữ FK ngoại dạng Long; chỉ quan hệ nội bộ module dùng JPA LAZY. Không sửa entity,
repository, migration hay nghiệp vụ của Account/Branch/Tour/Payment.

Mọi thay đổi vé khóa lịch trước, rồi khóa vé. Sức chứa thay đổi trên entity managed
trong cùng transaction; không dùng bulk update làm clear persistence context giữa luồng.

## Phân quyền

Role dùng tên authority hiện có, không có tiền tố ROLE_. Ngoài filter chain, service
có `@PreAuthorize`; policy kiểm tra phạm vi tài nguyên trong transaction.

| API | Quyền |
| --- | --- |
| GET `/api/schedules`, GET `/api/schedules/{id}` | Công khai; lịch của tour PUBLISHED. ADMIN xem mọi tour; STAFF chỉ xem tour ẩn ở chi nhánh được quản lý |
| GET `/api/schedules/my-assignments`, GET `/api/schedules/my-assignments/{id}` | GUIDE đang hoạt động, đúng lịch được phân công |
| POST `/api/schedules` | ADMIN hoặc STAFF đang hoạt động tại branchId được yêu cầu |
| PUT `/api/schedules/{id}` | ADMIN hoặc STAFF đúng chi nhánh lịch |
| POST `/api/schedules/{id}/reschedule` | ADMIN hoặc STAFF đúng chi nhánh lịch |
| PATCH `/api/schedules/{id}/status` | ADMIN hoặc STAFF đúng chi nhánh lịch |
| GET `/api/schedules/{id}/histories` | ADMIN, STAFF đúng chi nhánh, GUIDE được phân công |
| POST `/api/schedules/{id}/register` | CUSTOMER hoạt động, đăng ký cho chính JWT |
| GET `/api/schedules/{id}/participants` | ADMIN, STAFF đúng chi nhánh, GUIDE được phân công |
| GET `/api/participants/{id}` | Chủ vé, ADMIN, STAFF đúng chi nhánh |
| GET `/api/participants/by-user/{userId}` | Chính người dùng, ADMIN; STAFF chỉ nhận các vé thuộc chi nhánh được quản lý |
| POST `/api/participants/{id}/cancel` | Chủ vé, ADMIN, STAFF đúng chi nhánh; hủy thay khách phải có lý do |
| PATCH `/api/participants/{id}/confirm-payment` | ADMIN, STAFF đúng chi nhánh; chỉ xác nhận thủ công |
| PATCH `/api/participants/{id}/check-in` | ADMIN, STAFF đúng chi nhánh, GUIDE được phân công |
| PATCH `/api/participants/{id}/no-show` | ADMIN, STAFF đúng chi nhánh, GUIDE được phân công |
| GET `/api/participants/{id}/histories` | Chủ vé, ADMIN, STAFF đúng chi nhánh |
| GET `/api/participants/payment-reviews` | ADMIN; STAFF phải truyền branchId và đúng chi nhánh |

STAFF cần cả hồ sơ staff ACTIVE và branch ACTIVE. GUIDE cần đúng guideId được phân
công, hồ sơ ACTIVE, tài khoản ACTIVE và role GUIDE. ADMIN không phụ thuộc hồ sơ staff.
Khách chưa đăng nhập nhận 401 ở API được bảo vệ; thiếu quyền nhận 403.

## Vòng đời lịch

```text
OPEN ⇄ FULL → CLOSED → IN_PROGRESS → COMPLETED
                  ↘ CANCELLED (chỉ trước khởi hành)
```

- OPEN/FULL được tính từ số chỗ đã giữ; request đặt FULL bị từ chối.
- Đóng đăng ký từ OPEN hoặc FULL. Mở lại CLOSED trước hạn đăng ký bằng status OPEN;
  kết quả có thể là FULL nếu đã hết chỗ.
- IN_PROGRESS chỉ từ CLOSED, có guide hợp lệ, từ giờ bắt đầu đến trước giờ kết thúc.
- COMPLETED chỉ từ IN_PROGRESS sau giờ kết thúc. Vé CONFIRMED đã check-in chuyển
  COMPLETED; vé CONFIRMED chưa check-in chuyển NO_SHOW. Vé NO_SHOW giữ nguyên.
- CANCELLED chỉ trước khởi hành. Hủy tất cả vé PENDING/CONFIRMED còn giữ chỗ trong
  cùng transaction; tạo yêu cầu đối soát cho giao dịch hoặc xác nhận thủ công.
- Không mở lại lịch COMPLETED/CANCELLED. Dừng tour đang diễn ra chưa thuộc luồng hủy thông thường.
- Sửa guide/sức chứa/dời lịch chỉ trước khởi hành; sức chứa không nhỏ hơn số chỗ đã giữ.
- Guide không được trùng lịch theo userId; CLOSED vẫn giữ lịch guide; COMPLETED/CANCELLED không giữ.
- Tạo, sửa, dời lịch và đổi trạng thái đều có history; lưu người thao tác (Long), lý do và thời gian.

`registrationClosesAt` không sau startDatetime, phải ở tương lai lúc tạo/dời lịch;
nếu không truyền khi tạo thì mặc định bằng giờ khởi hành. Khi dời lịch mà không
truyền cutoff, giữ khoảng cách cutoff → khởi hành cũ. Dời deadline sớm sẽ rút ngắn
hold còn hiệu lực; dời muộn không kéo dài hold của khách.

Worker tự đóng OPEN/FULL thành CLOSED khi đến cutoff. Dù worker chưa chạy, backend
vẫn từ chối đăng ký/mở lại/nhận thanh toán quá cutoff.

## Vé và giữ chỗ

- Mỗi user có một participant_id cho một schedule; user và giá chốt lấy từ server.
- PENDING chiếm chỗ. `expiresAt = min(thời điểm đăng ký + hold-duration, cutoff)`.
- Hết hạn chuyển EXPIRED, trả đúng một chỗ; chạy lại hoặc tranh chấp với hủy không trừ chỗ hai lần.
- CONFIRMED chỉ khi thanh toán được xác nhận trong hạn và lịch còn nhận thanh toán.
- Khách được hủy trước giờ khởi hành, khi chưa check-in. MVP chưa tự tính phí hủy;
  vé đã xác nhận được đưa vào hàng đối soát, không tự đánh dấu đã hoàn tiền.
- Check-in chỉ vé CONFIRMED của lịch CLOSED/IN_PROGRESS, trong cửa sổ mặc định
  2 giờ trước đến 30 phút sau khởi hành, và trước giờ kết thúc tour.
- NO_SHOW chỉ sau cửa sổ điểm danh, trước giờ kết thúc, chưa check-in; không trả chỗ để bán lại sau khởi hành.
- `currentParticipants` là số chỗ đã giữ chưa giải phóng; ở lịch đã chạy có thể gồm
  vé COMPLETED/NO_SHOW để giữ số chỗ lịch sử, không phải số khách có mặt thực tế.

Tái đăng ký chỉ từ CANCELLED/EXPIRED: giữ participant_id, tạo UUID
`registrationReference` mới, reset check-in, snapshot giá mới. Lịch sử lưu UUID và
giá của từng lần. **Nếu participant đã có bất kỳ transaction nào, tạm từ chối tái
đăng ký (409)** vì schema Payment hiện chưa phân biệt giao dịch giữa các lần đặt.
Không xóa giao dịch cũ để vượt qua kiểm tra này. Member 6 cần bổ sung contract
tham chiếu lần đăng ký trước khi có thể hỗ trợ an toàn trường hợp đó.

## Request và response

Giữ `ApiResponse<T>` và đường dẫn cũ. Danh sách vẫn là mảng trong `data`.
Tất cả danh sách hỗ trợ `page=0&size=50`; page >= 0, size từ 1 đến 100.

Tạo lịch:

```json
{
  "eventId": 1,
  "branchId": 1,
  "guideId": 1,
  "startDatetime": "2026-12-20T07:00:00+07:00",
  "endDatetime": "2026-12-20T17:00:00+07:00",
  "maxParticipants": 20,
  "registrationClosesAt": "2026-12-19T17:00:00+07:00"
}
```

Đăng ký chỉ nhận `{"note":"Ăn chay"}` hoặc `{}`. Response vé bổ sung
`registrationReference` và `expiresAt`; response lịch bổ sung `registrationClosesAt`.

Đổi trạng thái luôn cần lý do:

```json
{"status":"CLOSED","changeReason":"Chốt danh sách bảo hiểm"}
```

Dời lịch nhận `newStartDatetime`, `newEndDatetime`, `changeReason` và
`registrationClosesAt` tùy chọn. PUT thông tin lịch nhận guideId (null để bỏ phân công)
và maxParticipants như trước.

Xác nhận thủ công **thay đổi request**: PATCH `/api/participants/{id}/confirm-payment`:

```json
{
  "registrationReference": "UUID từ response vé hiện tại",
  "paymentReference": "Mã biên nhận chuyển khoản/thu tiền mặt"
}
```

paymentReference tối đa 180 ký tự. Gọi lặp cùng UUID/chứng từ khi vé CONFIRMED không
ghi xác nhận lần hai. Vé đã có transaction phải dùng contract Payment, không xác
nhận thủ công để tránh thu/xác nhận hai lần. Request cũ không có body trả 400.

Hủy vé: body `{"reason":"Khách yêu cầu hủy"}`; có thể bỏ body nếu chủ vé tự hủy.
Lý do tối đa 2000 ký tự. Hủy lại vé CANCELLED/EXPIRED trả trạng thái hiện tại.

HTTP: 400 dữ liệu/thao tác trạng thái không hợp lệ; 401 chưa đăng nhập; 403 thiếu
quyền; 404 không tìm thấy/không được xem tour ẩn; 409 trùng vé, hết chỗ, hết hạn,
xung đột guide hoặc yêu cầu đối soát giao dịch.

## Contract Payment nội bộ

`ParticipantPaymentService.confirm(participantId, registrationReference, transactionId)`
không có endpoint HTTP. Chỉ identity backend có authority `SCHEDULE_PAYMENT` được gọi;
không cấp authority này cho CUSTOMER/STAFF/ADMIN hoặc nhận từ request người dùng.

Trước khi gọi, Payment phải xác thực callback và commit transaction SUCCEEDED
(hoặc flush nếu cùng transaction/DataSource). Module 5
đọc lại giao dịch từ database: đúng participant, loại PAYMENT, thành công, đúng số
tiền và không được tạo trước lần đăng ký hiện tại. Việc xác nhận không tin amount
hay status từ client. Kết quả:

- CONFIRMED: xác nhận thành công hoặc callback lặp của chính giao dịch đã xác nhận
  (kể cả sau khi vé COMPLETED/NO_SHOW).
- REVIEW_REQUIRED: sai lần đăng ký/số tiền, thanh toán muộn, vé đã hủy/hết hạn hoặc
  giao dịch đã vào hàng đối soát. Không khôi phục vé, không chiếm lại chỗ; lưu review
  chống trùng theo transaction_id. Payment phải tiếp tục quy trình đối soát.

Source tại 2026-10-28 đã có `ParticipantPaymentBridgeImpl.confirm` gọi contract này với
identity `SCHEDULE_PAYMENT`; `TransactionsServiceImpl.confirmParticipant` dùng bridge
cho giao dịch thành công. Mốc chuẩn hóa chỉ cập nhật import enum bắt buộc ở Payment,
không sửa luồng callback/query/retry. Việc có đường gọi trong source không thay thế
kiểm thử provider thật/end-to-end; kết quả kiểm tra riêng xem tài liệu chuẩn hóa.

`schedule_payment_reviews` là hàng yêu cầu đối soát bền vững: mã giao dịch (nếu có),
UUID lần đăng ký, số tiền snapshot, loại và lý do. Record liên quan giao dịch PENDING
cần xác minh kết quả provider trước khi hoàn tiền. Đây không phải bằng chứng đã
hoàn tiền, không phải số dư hoàn được, và không tự gọi provider. Consumer Payment
cần chống xử lý lặp theo review_id và tự lưu kết quả xử lý; hiện chưa có consumer.

## Migration và cấu hình

Migration mới `V5__schedule_lifecycle_and_audit.sql`, không sửa migration cũ:

| Bảng | Thay đổi |
| --- | --- |
| event_schedules | registration_closes_at; backfill bằng start_datetime; check cutoff <= start |
| event_participants | expires_at, registration_reference UUID, confirmation_reference; thêm EXPIRED; index vé PENDING quá hạn |
| event_schedule_history | changed_by Long, không map User |
| schedule_participant_history | PK history_id; FK nội bộ participant_id; UUID, action, trạng thái, giá, actor, lý do, thời gian |
| schedule_payment_reviews | PK review_id; FK nội bộ participant_id; transaction_id Long; dedup_key unique; số tiền snapshot |

PK/FK cũ giữ nguyên. Vé PENDING cũ được backfill hạn = min(registration_date + 15 phút,
giờ khởi hành); vé cũ đã quá hạn sẽ được worker giải phóng sau khi triển khai.

Các property có mặc định, không cần sửa `.env` hoặc cấu hình module khác:

```properties
schedule.hold-duration=PT15M
schedule.check-in-before=PT2H
schedule.check-in-after=PT30M
schedule.expiry.enabled=true
schedule.expiry.interval=PT1M
```

Worker xử lý theo lô, một transaction riêng cho mỗi lịch. Request đăng ký cũng dọn
hold quá hạn trong transaction có khóa lịch. Một lỗi worker được ghi log và thử lại.

## Kiểm thử

`src/test/java/com/coc/sba_treektour/modules/schedule/ScheduleIntegrationTests.java` kế thừa
8 tình huống tích hợp cũ và bổ sung quyền chi nhánh, vòng đời/thời gian, hết hạn,
manual receipt/UUID, callback lặp/muộn/sai tiền, history và các race condition.
Test module 5 tắt timer để kiểm soát thời gian; gọi worker thật qua service.

Chạy trên **database test riêng**, không dùng `.env` kết nối database chung:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55485/module5_test'
$env:DB_USERNAME = 'module5_test'
$env:DB_PASSWORD = ''
$env:FLYWAY_ENABLED = 'true'
.\gradlew.bat compileJava test --offline --console=plain
```

PostgreSQL 17.11 portable dùng trong lần kiểm tra này nằm trong build (git ignored).
Không chạy migration hoặc sửa dữ liệu trên Supabase/database dùng chung.
Kết quả chạy cuối cùng được ghi tại dòng module 5 trong MODULE_INTEGRATION_STATUS.md.

Kết quả lịch sử 2026-10-21: `compileJava`, `compileTestJava` và toàn bộ **60 tests PASS**,
gồm **21 test module 5**, không có test bị bỏ qua. Migration mới đã chạy thành công
trên database PostgreSQL 17.11 riêng; các module Account, Catalog và Tour đều pass.


## Chuẩn hóa 2026-10-28

Mốc 1–3 giữ nguyên endpoint, tên JSON, mảng danh sách, enum values, PK/FK, JPA mappings,
transactions và migration. DTO xác nhận thủ công đổi tên Java thành
`dto.request.ConfirmParticipantPaymentRequest`; response đối soát thành
`dto.response.SchedulePaymentReviewResponse`. API mốc 4–5 đã có canonical routes và alias legacy; xem tài liệu chuyển đổi, không xóa route cũ.

Kết quả kiểm tra mới nhất và bảng đổi tên: [MODULE_5_STRUCTURE_STANDARDIZATION.md](MODULE_5_STRUCTURE_STANDARDIZATION.md).


## Repository Spring Data (2026-10-28)

GuideScheduleRepository và ScheduleIntegrationRepository đã chuyển từ class JDBC sang
interface extends Repository<EventSchedule, Long>. Hai interface chỉ khai báo query cần dùng,
không mở CRUD cho bảng của module khác, không tạo entity giả hoặc thay schema.
Khóa guide giữ cùng hash key tour-guide:userId và pg_advisory_xact_lock, bắt buộc có transaction
đang hoạt động (Propagation.MANDATORY). Hàm lock chạy trong FROM và trả scalar 1 để Spring Data
mapping được, không dùng Modifying vì đây là SELECT lấy khóa.

Code runtime module 5 không còn JDBC trực tiếp/RowMapper. JdbcTemplate trong integration tests
chỉ phục vụ seed/cleanup và điều chỉnh fixture thời gian, không phải repository nghiệp vụ.
Kiểm tra cuối: compileJava + compileTestJava và toàn bộ 181 tests PASS (32 Schedule/59 Payment),
0 skipped trên PostgreSQL 17.11 riêng module5_spring_data_20261028; Flyway validate 9 migrations
và Hibernate validate bật. Các test phân trang/quyền assignment, xác nhận Payment/review,
tranh chấp guide cùng userId và giữ chỗ đều PASS. API/schema/migration không đổi.
Chi tiết trạng thái: MODULE_INTEGRATION_STATUS.md.


## Rà soát cách dùng Spring Data (2026-10-28)

- Hai lookup khóa theo ID dùng derived `findLockedById` + `@Lock(PESSIMISTIC_WRITE)`,
  bỏ JPQL `select ... where id=:id`. Giữ adapter `findForUpdate` để không đổi contract
  repository đang được Payment gọi. Không gắn lock lên findById thông thường.
- Worker lấy lịch đến hạn qua derived query cutoff <= now + status IN OPEN/FULL,
  closed projection chỉ ID và Pageable/order by ID. Giữ adapter findDueToClose.
- Bỏ 5 khai báo không có caller trong source: danh sách lịch theo event/branch/status,
  lịch sử lịch không phân trang, danh sách vé theo user không phân trang.
- Giữ JPQL lấy scheduleId của vé để khóa lịch trước vé mà không tải entity; giữ query
  distinct schedule IDs của vé quá hạn để phân trang theo lịch, không theo từng vé.
- Query lọc scope/đọc dữ liệu liên module, projection assignment/Payment và advisory
  lock PostgreSQL không có CRUD/derived method tương đương trên entity Schedule hiện tại.
  @Query không phải API deprecated; không ép thành method name rất dài hoặc tạo entity giả.
- Kiểm tra các lớp controller/service/entity/DTO/security/scheduler: đang dùng Jakarta,
  java.time, constructor injection và SecurityFilterChain lambda. Không thấy các API cũ
  getOne/getById, javax, Date/SimpleDateFormat hay WebSecurityConfigurerAdapter trong module 5.
  @Autowired ở ScheduleSettings dùng để chọn giữa hai constructor, vẫn hợp lệ.
- Không nâng dependency toàn dự án hoặc sửa module khác cho thay đổi này.

Nguồn đối chiếu: [Spring Data query methods](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html),
[Spring Data locking](https://docs.spring.io/spring-data/jpa/reference/4.0/jpa/locking.html).
Compile được bật thêm -Xlint:deprecation/-Xlint:removal trong init script local (build, git ignored);
kết quả cuối cập nhật tại MODULE_INTEGRATION_STATUS.md.

Kết quả lint biên dịch: không có cảnh báo deprecated/removal thuộc module 5. Có cảnh báo
ngoài phạm vi: common/config/JwtAuthenticationFilter dùng org.springframework.lang.NonNull;
TourExceptionHandler/ImageValidator và ImageValidatorTests dùng HttpStatus.PAYLOAD_TOO_LARGE.
Giữ nguyên source các owner khác; không coi những cảnh báo này là lỗi đã sửa.

Kết quả sau rà soát: compileJava + compileTestJava với lint và toàn bộ **182 tests PASS**,
0 skipped (33 Schedule/59 Payment); PostgreSQL 17.11 module5_review_20261028 riêng,
Flyway áp dụng/validate 9 migrations và Hibernate validate bật. Không đổi API/JSON/schema
hoặc migration; không sửa source ngoài module 5 và các tài liệu trạng thái của nó.
