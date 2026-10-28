# TerraPeak Outpost — Tour & guide API

Module 4, nhánh chuẩn hóa `feature/tour-guide-structure` từ main `3df868f` (2026-10-24). Backend cung cấp `/api`; frontend nằm trong repo riêng. Chuẩn cấu trúc: [PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md).

## Shared response format

Tour endpoints now reuse `common.response.ApiResponse` without changing the shared class. All JSON responses, including security errors, have `status`, `message`, `data`, `timestamp`. HTTP 204 image deletion still has no body. Request bodies and database schema are unchanged.

```json
{
  "status": 200,
  "message": "Thành công",
  "data": {"eventId": 1, "eventName": "Ta Nang"},
  "timestamp": "2026-10-09T22:00:00+07:00"
}
```

The data example above is abbreviated. Existing clients must now read `data.eventId`, `data.images`, `data.items`, etc. Creation returns both HTTP 201 and JSON status 201. A tour's business status is `data.status`; the root `status` is the HTTP status number. Internal EventReference/GuideReference service contracts are unchanged.


## Customer flow and ownership

Anonymous customers browse published trekking/camping events, read details/images and active guide profiles. Module 5 supplies public schedules, capacity and the guide assigned to each schedule. Selecting **Register** requires authentication through module 1, then module 5 rechecks price/capacity and creates the participant. Module 6 takes payment. These registration/payment endpoints are not implemented by this module.

An event is reusable content, not one departure. Public guide profiles do not imply assignment to an event. One user may have multiple professional profiles; scheduling MUST check overlapping assignments by `userId`, not only `guideId`.

## Setup and database

Requires Java 25, PostgreSQL and the shared Account/Branch/Order tables expected by existing JPA entities.

- Database PostgreSQL trống dùng Flyway với `FLYWAY_ENABLED=true`; V1/V3/V4/V5 và các migration phiên bản ngày được chạy theo lịch sử. Không import lại ERD hoặc sửa migration đã chạy trên DB nhóm.
- Flyway `V2026100901__tour_and_guide.sql` owns only `tour_events`, `event_details`, `event_images`, `tour_guides`. On a fresh schema, Account must create `users` first. On a schema created from the shared ERD, it adds the Cloudinary field, constraints and indexes without replacing existing tables/data.
- PKs: `event_id`, `event_detail_id`, `image_id`, `guide_id` (`BIGINT` identity). Internal FKs: details/images → event; details is one-to-one. External FK: guides → users, exposed as scalar `Long userId` until Account is READY_TO_MAP.
- `event_images.cloudinary_public_id` is nullable to support legacy ERD images. New uploads always save the asset identifier; it is never returned to clients.
- Unique `(user_id, lower(btrim(specialization)))` allows several distinct specializations per user and prevents duplicates, including concurrent inserts. Blank/null specializations and unsupported event/difficulty values must be corrected before migrating legacy data; migration fails rather than silently changing data.
- The migration version is part of the shared Flyway sequence; coordinate future versions with the team. Never edit a migration after it has been applied to a shared database.

Configure DB variables from `.env.example`, plus `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Missing Cloudinary credentials do not prevent startup or browsing; upload/delete of managed assets returns `503 STORAGE_NOT_CONFIGURED`.

Run `bash gradlew bootRun`. The Gradle wrapper currently has no executable bit, so use `bash gradlew`.

## Authentication — JWT integration test branch

Nhánh chuẩn hóa dựa trên main `3df868f`; giữ nguyên JWT và cách tải role của member 1. Tour reuses the JWT service/filter with an account-status guard that rejects inactive/blocked accounts. It does not change Account code or schema. The temporary fallback security chain is removed; member 1 owns authentication for other modules.

1. POST `/api/auth/login` with `email` and `password`.
2. Read `data.token`.
3. Send `Authorization: Bearer <token>` to tour admin endpoints; the account requires `ADMIN`.

GET `/api/events`, `/api/events/{eventId}`, `/api/guides`, `/api/guides/{guideId}` remain public. Tour no longer accepts HTTP Basic or cookie authentication; stateless Bearer requests do not require CSRF tokens. `/api/tour/csrf` is removed on this branch.

`TourExceptionHandler` takes priority for tour controllers so member 1's general handler does not replace tour HTTP status/error fields.

See [JWT_TEST.md](JWT_TEST.md) for local commands. The integration is for local testing; member 1 still needs to address shared JWT key configuration and role/status loading for endpoints outside tour. No CORS policy was added.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/events` | Published event summaries |
| GET | `/api/events/{eventId}` | Published event details and images |
| GET | `/api/guides` | Active guide profiles with active accounts |
| GET | `/api/guides/{guideId}` | Public professional profile |
| GET | `/api/admin/events` | Events in all states |
| GET | `/api/admin/events/{eventId}` | Full event in any state |
| POST | `/api/admin/events` | Create draft |
| PUT | `/api/admin/events/{eventId}` | Replace editable content/details |
| PATCH | `/api/admin/events/{eventId}/status` | Publish or archive |
| POST | `/api/admin/events/{eventId}/images` | Multipart image upload |
| PATCH | `/api/admin/events/{eventId}/images/{imageId}` | Replace image description |
| DELETE | `/api/admin/events/{eventId}/images/{imageId}` | Delete image |
| GET | `/api/admin/guides` | All professional profiles |
| GET | `/api/admin/guides/{guideId}` | Administrative profile |
| POST | `/api/admin/guides` | Add specialization for an existing active account |
| POST | `/api/admin/guides/with-account` | Create a GUIDE account and profile together |
| PUT | `/api/admin/guides/{guideId}` | Replace experience/specialization/bio/languages |
| PUT | `/api/admin/guides/{guideId}/avatar` | Upload/replace avatar (multipart file) |
| DELETE | `/api/admin/guides/{guideId}/avatar` | Delete avatar |
| PATCH | `/api/admin/guides/{guideId}/status` | Activate/deactivate profile |

Tạo event/guide/tài khoản kèm guide và upload ảnh event trả 201; upload/thay avatar trả 200; xóa ảnh event/avatar trả 204; các thao tác thành công khác trả 200. Tour and guide deletion is intentionally replaced by archive/deactivation to preserve schedule references.

### Filters and pagination

Every list accepts `page` (default 0, minimum 0), `size` (default 20, range 1–100).

Events: `keyword` (case-insensitive name substring), `eventType`, `difficultyLevel`, `minPrice`, `maxPrice`. Admin also accepts `status`. Prices must be nonnegative and min ≤ max. Ordering is `createdAt DESC, eventId DESC`.

Guides: case-insensitive `specialization` substring; admin also accepts `status`, `userId`. Ordering is `guideId DESC`. `%` and `_` in searches are literal characters, not wildcard operators.

```json
{"status":200,"message":"Thành công","data":{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0},"timestamp":"2026-10-09T22:00:00+07:00"}
```

### Event create/update

```json
{
  "eventName": "Trekking Ta Nang — Phan Dung",
  "eventType": "TREKKING",
  "description": "Two days trekking with an overnight camp.",
  "location": "Lâm Đồng",
  "basePrice": 1500000.00,
  "details": {
    "durationMinutes": 2880,
    "difficultyLevel": "MODERATE",
    "meetingPoint": "TerraPeak Outpost",
    "requirements": "Trekking shoes, raincoat and drinking water."
  }
}
```

`eventName` is required, trimmed, ≤200 characters; `eventType` is `TREKKING` or `CAMPING`. `basePrice` is required, nonnegative, with ≤16 integer digits and ≤2 fractional digits. Currency is the shared app's money unit; no currency conversion is performed.

Description ≤50,000 characters; location, meetingPoint and requirements ≤10,000 each. Details may be omitted/null for drafts; PUT with absent/null details removes the current details. Duration, if supplied, is a positive number of minutes; difficulty is `EASY`, `MODERATE`, `HARD`.

The response `data` contains `eventId`, input fields, `status`, `createdAt`, `updatedAt` and `images: [{imageId,imageUrl,description}]`. Timestamps are ISO 8601 with an offset. Summaries contain `eventId,eventName,eventType,location,basePrice,status,difficultyLevel,coverImageUrl`; cover is the oldest remaining image by imageId. No image ordering/cover-selection UI is included in v1.

### Status and publication

```json
{"status":"PUBLISHED"}
```

Transitions: DRAFT → PUBLISHED or ARCHIVED; PUBLISHED → ARCHIVED. Repeating the current state is idempotent. ARCHIVED is read-only. Public requests for draft/archived events return 404.

Publication requires nonblank description, location, meeting point, requirements, a duration, difficulty and ≥1 image. Editing a published event must preserve these conditions; deleting its last image is rejected. Mutations lock the event row to serialize publishing, editing and image removal. Archiving does not cancel schedules or participants. Changes to basePrice do not change already registered prices; module 5 snapshots its registration price.

### Images

POST multipart fields: `file` (required), `description` (optional, ≤1,000 characters). Accept JPEG/PNG/WebP, ≤5 MiB; request body limit is 6 MiB. Server checks MIME/signature; Cloudinary decodes the actual image. Upload uses backend-signed requests; secrets stay on the server.

Image description PATCH body:

```json
{"description":"Sunrise on the trail"}
```

Image must belong to the path's event. Upload failure creates no DB row. On database rollback after upload, a transaction callback deletes the new Cloudinary asset; failures are logged with publicId for manual cleanup. Deletion calls Cloudinary first, retains the DB row on storage failure and treats an already missing asset as successfully deleted.

Cloudinary and PostgreSQL cannot commit atomically. If Cloudinary deletion succeeds but DB commit fails, retry DELETE to remove the remaining row; delivery URLs may be temporarily broken. CDN invalidation is asynchronous. No automatic cleanup queue is included in v1.

### Guides

Create:

```json
{"userId":123,"experienceYears":3,"specialization":"Mountain trekking"}
```

Use an existing active userId; 123 is only a placeholder. Each profile has one nonblank specialization (≤1,000 characters) and integer experienceYears ≥0. Distinct specializations may reference the same account; case-insensitive duplicates after trimming are rejected by the existing DB unique index (409), without an extra preflight query. New profiles are ACTIVE. PUT nhận experienceYears/specialization/bio/languages; userId không thay đổi. Bio tùy chọn ≤5.000 ký tự; languages tùy chọn, tối đa 10 tên không trống, mỗi tên ≤50 ký tự. Bỏ hai trường mới trong PUT sẽ đưa chúng về null/[], không tác động avatar.

Status PATCH accepts `ACTIVE`/`INACTIVE`. Activation requires an active account. Inactive guide/account profiles are hidden publicly.

Public response data:

```json
{"guideId":12,"displayName":"Nguyen An","experienceYears":3,"specialization":"Mountain trekking","bio":null,"languages":[],"avatarUrl":null}
```

Admin response additionally includes userId and status. GET guide không lộ email, phone, password, role hoặc avatarPublicId. Tên/trạng thái tài khoản được đọc theo lô qua `tour.repository.AccountReadRepository`, chỉ SELECT user_id/full_name/status; kiểm tra active thuộc GuideServiceImpl. Luồng ADMIN tạo tài khoản mới là ngoại lệ: POST `/api/admin/guides/with-account` trả email/mật khẩu tạm một lần theo contract đã chốt, Cache-Control no-store; chi tiết tại [GUIDE_PROFILE.md](GUIDE_PROFILE.md).

## Errors and integration contracts

Module controller and tour security errors reuse the same envelope; stable error codes and field-level details are inside data:

```json
{"status":400,"message":"Request validation failed","data":{"code":"INVALID_INPUT","fieldErrors":{"eventName":"must not be blank"}},"timestamp":"2026-10-09T22:00:00+07:00"}
```

400 malformed/invalid input; 401 authentication required; 403 insufficient permission; 404 missing/hidden resource; 409 invalid transition, missing publication requirements or duplicate/reference conflict; 413 oversized image; 502 storage failure; 503 storage not configured.

Internal read services for module 5:

- `EventService.reference(eventId)` → `EventReference(eventId,status,basePrice)`.
- `GuideService.reference(guideId)` → `GuideReference(guideId,userId,status,accountActive)`.

These are lookups, not reservations or schedule validators. Module 5 must require PUBLISHED events, ACTIVE guides/accounts for new scheduling/registration and own the transactions for capacity, price snapshots and overlapping schedules. Existing records remain readable when events are archived or guides deactivated.

## Verification

`bash gradlew compileJava test` requires a dedicated test database configured with DB_URL/DB_USERNAME/DB_PASSWORD. Existing contextLoads also requires the shared schema. Never point integration tests at a production database. API integration tests roll back their data; Cloudinary is mocked and no live assets are uploaded.

Tests cover anonymous browsing, admin/customer/anonymous permissions, JWT stateless writes, draft/publish/archive, validation/filters/pagination, image IDs and ownership, storage errors, rollback compensation, guide privacy/active accounts and duplicate specialization at both service and DB level.

Live Cloudinary calls still require a manual smoke test with configured credentials. Frontend tour/guide được triển khai ở repo riêng. Module 4 tái sử dụng dịch vụ Account để tạo guide kèm tài khoản; lịch/đăng ký và thanh toán do module 5/6 sở hữu.

JWT integration adds real login checks for ADMIN, CUSTOMER, STAFF and GUIDE, rejection of disabled/blocked accounts and invalid tokens. 19 tests pass on a separate PostgreSQL database.


## Cấu trúc package tour — cập nhật 2026-10-28

```text
modules/tour/
  controller/                 AdminEventController, PublicEventController,
                              AdminGuideController, PublicGuideController
  dto/request/                EventInput, DetailsInput, ItineraryDayInput, ActivityInput,
                              PackingItemInput, EventStatusInput, ImageDescriptionInput,
                              GuideCreateInput, GuideUpdateInput, GuideStatusInput, GuideRegistrationInput
  dto/response/               EventResponse, DetailsResponse, EventSummary, ImageResponse,
                              ItineraryDayResponse, ActivityResponse, PackingItemResponse, EventReference,
                              AdminGuideResponse, PublicGuideResponse, GuideReference,
                              GuideRegistrationResponse, PageResponse
  entity/                     TourEvent, EventDetail, EventImage, ItineraryDay, PackingItem, TourGuide
  enums/                      EventType, EventStatus, DifficultyLevel, GuideStatus
  repository/                 EventRepository, ImageRepository, GuideRepository, AccountReadRepository
  service/                    EventService, GuideService, GuideRegistrationService (interfaces)
  service/impl/               EventServiceImpl, GuideServiceImpl, GuideRegistrationServiceImpl
  storage/                    CloudinaryImageStorage
  validator/                  ImageValidator
  exception/                  TourException, TourExceptionHandler
  config/                     TourSecurityConfig
  util/                       TourQueries
```

Controller/module khác inject interface, implementation dùng `@RequiredArgsConstructor` và `@Override`. Transaction đọc/ghi được giữ trong implementation. Criteria và JDBC query nằm trong repository sở hữu đầu đọc. Không tạo interface giả cho storage/validator/util. Entity dùng `@Getter`, constructor JPA protected, constructor nghiệp vụ và setter cần thiết; ID/timestamp không cho sửa qua setter.

Test thuộc `src/test/java/com/coc/sba_treektour/modules/tour`, package khớp đường dẫn; unit test được khởi tạo Impl với mock, controller/service thực tế không phụ thuộc Impl.

Refactor không đổi endpoint, JSON, HTTP status, field, validation, enum values, bảng/cột/PK/FK hoặc migration. `DetailsResponse` tách khỏi request nhưng giữ các field JSON cũ. Java caller cập nhật import DTO/enum/exception theo package mới. Schedule chỉ đổi import EventStatus/GuideStatus; không sửa nghiệp vụ module 5. Event/GuideReference vẫn giữ cùng field và cùng ý nghĩa.

`tour` chứa cả nội dung TREKKING/CAMPING (phân biệt bằng eventType), ảnh và hồ sơ hướng dẫn viên. Không còn package `modules.event`; giữ tên class Event* và endpoint `/api/events`, `/api/admin/events`. Java caller chuyển import từ `modules.event.*` sang `modules.tour.*`. TourExceptionHandler xử lý controller tour/schedule; phân quyền JWT giữ nguyên.

Kết quả chuẩn hóa: compileJava + compileTestJava + **147 tests PASS** trên PostgreSQL local riêng; frontend `npm run lint` và `npm run build` PASS. Kiểm tra tự động xác nhận mapping endpoint/method và header DTO (field/validation) giữ nguyên, migrations không đổi; ngoài module 4 chỉ sửa import.

Sau khi di chuyển package, cần rebuild sạch output biên dịch của IDE/Gradle trước khi chạy lại để tránh Spring quét class cũ gây trùng bean. Không bật bean overriding để che lỗi. Lần refactor này dùng build output riêng `build/tour-structure-verification`, không restart backend đang chạy hoặc chạy migration DB nhóm.


## Nội dung chi tiết theo FE mới — 2026-10-24

Nhánh `feature/tour-detailed-content`. POST `/api/admin/events` và PUT
`/api/admin/events/{eventId}` giữ các trường cũ, bổ sung trong `details`:

| Trường | Nội dung và giới hạn |
| --- | --- |
| `itinerary` | Danh sách tối đa 30 ngày; thứ tự `day` bắt đầu từ 1, liên tục, không trùng |
| `itinerary[].title` | Tên chặng bắt buộc, tối đa 200 ký tự |
| `itinerary[].summary` | Tóm tắt ngày, tối đa 2.000 ký tự |
| `itinerary[].distanceKm`, `elevation` | Chuỗi hiển thị có đơn vị, tối đa 100 ký tự, ví dụ `8–10 km`, `Lên 450 m`; không dùng tính giá hay kiểm tra sức khỏe |
| `itinerary[].highlights` | Tối đa 20 ý, mỗi ý không rỗng và tối đa 300 ký tự |
| `itinerary[].activities` | Tối đa 50 hoạt động, giữ thứ tự nhập |
| `activities[].time`, `title` | Bắt buộc; tối đa 100/200 ký tự. `time` là mốc tương đối trong ngày như `07:00 – 08:00`, không phải ngày khởi hành |
| `activities[].location`, `description` | Địa điểm cụ thể/mô tả; tối đa 1.000/2.000 ký tự |
| `activities[].icon` | `meet`, `hike`, `food`, `summit`, `camp`, `rest`, `flag`; không gửi/null mặc định `hike` |
| `packingItems` | Tối đa 100 mục; `text` bắt buộc, tối đa 1.000 ký tự; `required` bắt buộc là boolean |

Mẫu JSON đầy đủ: [examples/create-tour-rich.json](examples/create-tour-rich.json).
Sau khi tạo vẫn là DRAFT; thêm ảnh rồi đổi trạng thái PUBLISHED như trước.
GET admin/public chi tiết trả cùng nội dung mới; khách chỉ đọc tour PUBLISHED.
Response lỗi lồng nhau vẫn là HTTP 400 `INVALID_INPUT`, kèm `data.fieldErrors`
(ví dụ `details.itinerary[0].activities[0].title`). Ngày không liên tục trả 400
với thông báo nghiệp vụ. Không thay đổi `EventReference` của member 5.

**PUT thay toàn bộ nội dung:** FE phải tải và gửi đủ các trường muốn giữ.
Danh sách bị bỏ qua/null được chuẩn hóa thành `[]`; gửi `[]` để xóa nội dung.
Request cũ vẫn hợp lệ nhưng không giữ nội dung mới nếu client không gửi lại.
`details: null` tiếp tục xóa toàn bộ chi tiết như contract cũ.

Migration mới `V6__event_rich_content.sql` thêm đúng hai cột JSONB
`event_details.itinerary`, `event_details.packing_items`, NOT NULL, mặc định `[]`.
Tour cũ tự có danh sách rỗng. Không thay PK `event_detail_id`, FK unique `event_id`,
bảng `tour_events`, `event_images`, enum hay schema Schedule/Account/Payment.
Không sửa migration đã áp dụng. Cần migration này trước khi chạy backend mới.
Đã kiểm thử với Flyway bật trên PostgreSQL local riêng; chưa áp dụng DB Supabase chung.

FE có editor dùng chung cho tạo/sửa, tự đánh lại ngày sau khi xóa. Trang chi tiết
đọc nội dung API, hiển thị đang cập nhật khi chưa có; không tự sinh lịch trình,
cự ly, độ cao hay checklist. Số chỗ và hướng dẫn viên vẫn theo từng lịch khởi hành
của member 5. Các cam kết dịch vụ chung đang là nội dung tĩnh của FE, không phải
trường mới trong API tạo tour.

Kiểm tra: compileJava + compileTestJava và **150 tests PASS** (0 skipped), gồm
lưu/đọc JSONB sau khi clear persistence context, sửa rồi đọc lại, công bố/đọc công khai,
ẩn nháp, giữ request cũ, xóa danh sách và từ chối dữ liệu lồng nhau sai/giới hạn.
FE lint/build PASS. Đã thử tạo và sửa qua browser → API → DB local, rồi xem cả
hai ngày và checklist trên trang khách. Ảnh cho bước công bố là fixture legacy local;
không gọi Cloudinary hay gửi email thật trong lượt kiểm thử này.
