# TerraPeak Outpost — Tour & guide API

Module 4, branch `feature/tour-guide`. Backend only; controllers use `/api`.

## Shared response format

Tour endpoints now reuse `common.response.ApiResponse` without changing the shared class. All JSON responses, including CSRF and security errors, have `status`, `message`, `data`, `timestamp`. HTTP 204 image deletion still has no body. Request bodies and database schema are unchanged.

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

- For a new local development database, the team's `doc/erd_postgresql.sql` creates the shared baseline. Run it only on an empty database, then enable `FLYWAY_BASELINE_ON_MIGRATE=true` for initial onboarding. Do not run the full SQL as this module's migration.
- Flyway `V2026100901__tour_and_guide.sql` owns only `tour_events`, `event_details`, `event_images`, `tour_guides`. On a fresh schema, Account must create `users` first. On a schema created from the shared ERD, it adds the Cloudinary field, constraints and indexes without replacing existing tables/data.
- PKs: `event_id`, `event_detail_id`, `image_id`, `guide_id` (`BIGINT` identity). Internal FKs: details/images → event; details is one-to-one. External FK: guides → users, exposed as scalar `Long userId` until Account is READY_TO_MAP.
- `event_images.cloudinary_public_id` is nullable to support legacy ERD images. New uploads always save the asset identifier; it is never returned to clients.
- Unique `(user_id, lower(btrim(specialization)))` allows several distinct specializations per user and prevents duplicates, including concurrent inserts. Blank/null specializations and unsupported event/difficulty values must be corrected before migrating legacy data; migration fails rather than silently changing data.
- The migration version is part of the shared Flyway sequence; coordinate future versions with the team. Never edit a migration after it has been applied to a shared database.

Configure DB variables from `.env.example`, plus `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Missing Cloudinary credentials do not prevent startup or browsing; upload/delete of managed assets returns `503 STORAGE_NOT_CONFIGURED`.

Run `bash gradlew bootRun`. The Gradle wrapper currently has no executable bit, so use `bash gradlew`.

## Authentication and CSRF

Only GET `/api/events`, `/api/events/{eventId}`, `/api/guides`, `/api/guides/{guideId}` and `/api/tour/csrf` are public. Admin endpoints require authority `ROLE_ADMIN`. CSRF is enabled, including for multipart uploads.

1. GET `/api/tour/csrf` and retain its session cookie.
2. Read `data.headerName` and `data.token` from its response.
3. Send that header and cookie on POST/PUT/PATCH/DELETE, together with the authenticated session or HTTP Basic credentials.

Account now has an initial AuthService, but its HTTP login and shared security integration are not available yet. This module does not create accounts or grant roles. For local development only, Spring Boot's default user can be configured through `SPRING_SECURITY_USER_NAME`, `SPRING_SECURITY_USER_PASSWORD`, `SPRING_SECURITY_USER_ROLES=ADMIN` when no custom authentication provider is installed. Real users from `users` are not authenticated automatically by this module.

`TourSecurityConfig` scopes its first filter chain to these endpoints. A final fallback preserves Boot's authenticated protection outside the module. Member 1 must consolidate/replace this fallback when introducing shared authentication and make sure the tour chain uses the shared authentication provider. No CORS policy was added; the intended initial client is same-origin.

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
| PUT | `/api/admin/guides/{guideId}` | Replace years of experience/specialization |
| PATCH | `/api/admin/guides/{guideId}/status` | Activate/deactivate profile |

Create/upload returns 201; delete image returns 204; other successes return 200. Tour and guide deletion is intentionally replaced by archive/deactivation to preserve schedule references.

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
  "location": "Lam Dong — Binh Thuan",
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

Use an existing active userId; 123 is only a placeholder. Each profile has one nonblank specialization (≤1,000 characters) and integer experienceYears ≥0. Distinct specializations may reference the same account; case-insensitive duplicates after trimming are rejected by the existing DB unique index (409), without an extra preflight query. New profiles are ACTIVE. PUT accepts only experienceYears/specialization; userId is immutable.

Status PATCH accepts `ACTIVE`/`INACTIVE`. Activation requires an active account. Inactive guide/account profiles are hidden publicly.

Public response data:

```json
{"guideId":12,"displayName":"Nguyen An","experienceYears":3,"specialization":"Mountain trekking"}
```

Admin response additionally includes userId and status. Neither response exposes email, phone, password or role. Names/account status are read through a read-only adapter selecting only user_id, full_name, status. Replace that adapter with Account's published read contract when ready.

## Errors and integration contracts

Module controller and tour security errors reuse the same envelope; stable error codes and field-level details are inside data:

```json
{"status":400,"message":"Request validation failed","data":{"code":"INVALID_INPUT","fieldErrors":{"eventName":"must not be blank"}},"timestamp":"2026-10-09T22:00:00+07:00"}
```

400 malformed/invalid input; 401 authentication required; 403 insufficient permission/CSRF; 404 missing/hidden resource; 409 invalid transition, missing publication requirements or duplicate/reference conflict; 413 oversized image; 502 storage failure; 503 storage not configured.

Internal read services for module 5:

- `EventService.reference(eventId)` → `EventReference(eventId,status,basePrice)`.
- `GuideService.reference(guideId)` → `GuideReference(guideId,userId,status,accountActive)`.

These are lookups, not reservations or schedule validators. Module 5 must require PUBLISHED events, ACTIVE guides/accounts for new scheduling/registration and own the transactions for capacity, price snapshots and overlapping schedules. Existing records remain readable when events are archived or guides deactivated.

## Verification

`bash gradlew compileJava test` requires a dedicated test database configured with DB_URL/DB_USERNAME/DB_PASSWORD. Existing contextLoads also requires the shared schema. Never point integration tests at a production database. API integration tests roll back their data; Cloudinary is mocked and no live assets are uploaded.

Tests cover anonymous browsing, admin/customer/anonymous permissions, CSRF, draft/publish/archive, validation/filters/pagination, image IDs and ownership, storage errors, rollback compensation, guide privacy/active accounts and duplicate specialization at both service and DB level.

Live Cloudinary calls still require a manual smoke test with configured credentials. The implementation does not supply a UI, login/account API, schedules, registrations or payments.
