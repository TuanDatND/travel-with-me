# Tour image layout (Member 4)

## Database and compatibility

Migration: `V2026103101__tour_image_layout.sql`.

Adds `event_images.sort_order` (non-negative integer) and `day_number` (nullable integer 1–30), plus an event/order index. Existing image IDs, event IDs, URLs, Cloudinary assets and descriptions are preserved. Existing rows are ranked by `image_id` within their tour, preserving the previous cover and gallery order. No migration of another member is edited.

Restart the backend normally with Flyway enabled to apply this migration before using the new FE controls. Do not paste the migration into Supabase separately if Flyway manages this database. Existing upload, delete, description, create/update tour and public read endpoints remain available.

## API

`PUT /api/admin/events/{eventId}/images/layout` — ADMIN only.

```json
{
  "images": [
    { "imageId": 12, "dayNumber": 2 },
    { "imageId": 10, "dayNumber": 1 },
    { "imageId": 11, "dayNumber": null }
  ]
}
```

Use real image IDs from that tour. Include every current image exactly once. Array order is the gallery order; its first image is the cover. `dayNumber: null` clears an assignment (gallery only). Multiple photos may belong to a day; an image belongs to at most one day. All images still appear in the main gallery.

The response uses the standard envelope and an ordered array of image metadata. Existing metadata fields `imageId`, `imageUrl`, `description` remain; additive fields are `sortOrder`, `dayNumber`, `cover`. Tour detail `images` includes the same metadata. Public list `coverImageUrl` follows the chosen cover.

- Duplicate IDs, null entries and nonexistent/out-of-range days: 400.
- Foreign/missing image ID: 404.
- Incomplete library (e.g. another admin added an image): 409; reload photos and save the complete library.
- Archived tour: 409; customer/anonymous writes are rejected by existing security.

The service locks the tour and validates the entire layout before mutation. A successful save updates metadata only, without Cloudinary reuploads/deletes. Uploads append to the gallery; deleting the cover promotes the next image. Removing itinerary days clears those assignments without deleting images. Positions may have gaps after deletion; array order remains authoritative.

## FE workflow

Create form: select files, choose cover/order and optional itinerary day. Save a draft, upload sequentially, then save the complete layout. Successful uploads and draft ID are retained after a known failure; remaining uploads can resume. Creation, provider uploads and layout save are separate requests, not one distributed transaction. If a response is lost, check the saved draft/library before retrying uploads.

Existing tour library: preview cover/order/day changes, choose **Save photo layout**, or **Discard & reload photos**. Save/discard pending layout before upload/delete. Archived libraries are read-only. Tour detail renders only explicitly assigned day photos, with no inferred day association.

## Verification — 2026-10-31

- `compileJava`, `compileTestJava`: passed.
- Full backend suite on isolated local PostgreSQL: 210 passed, 7 pre-existing revenue repository tests skipped, 0 failures/errors. Includes 5 new image-layout integration cases: persistence/public cover, invalid/foreign/incomplete requests and atomicity, removed day cleanup, legacy upload/delete compatibility, ADMIN/archive guards.
- Actual migration SQL also tested on legacy multi-tour image fixtures in a temporary schema: previous ordering/cover preserved, transaction rolled back.
- Flyway validation and Hibernate schema validation enabled; no shared-database/provider writes used for tests.

- FE: 36 configured tests passed (11 tour/guide/API, 21 schedule, 4 navigation), TypeScript and production build passed; existing chunk-size warning remains.
- Live HTTP on isolated local backend 8084: authenticated ADMIN changed cover/order/day, published the local fixture and read matching metadata publicly. Browser at local FE 3004 saved a new cover and day assignment through the actual endpoint, cleared the unsaved indicator, and PostgreSQL confirmed the same values. Day 2 rendered the two assigned photos. Images used existing public URLs in a local test fixture; no Cloudinary upload or shared database write.
- Actual provider uploads, partial network-response loss, concurrency load tests and tablet/mobile behavior were not browser-tested.

- The already-running main backend on port 8080 reloaded the compiled code automatically. A read-only GET of its existing tour 60 returned HTTP 200 and the new image metadata. No manual SQL/migration command was sent to that backend database. Migration application remains owned by normal application startup/Flyway.

## Pre-push sync

Backend branch `feature/tour-image-layout` was fast-forwarded to latest `origin/main` (`e418bd2`) before committing this feature. The only stash-apply conflict was the integration-status document; both member updates were retained. Recompiled from generated Java output after main package moves (without deleting the local PostgreSQL data under `build/`). Full suite: 217 total, 210 passed, 7 pre-existing skips, 0 failures/errors; Flyway and Hibernate validation enabled against a fresh isolated local database. Feature changes are restricted to the tour module, its tests, migration and documentation.
