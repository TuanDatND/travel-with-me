# Hồ sơ hướng dẫn viên: giới thiệu, ngôn ngữ và ảnh

> Cập nhật vận hành 2026-10-24: đã bật `FLYWAY_ENABLED=true` trong `.env` local, áp dụng V2026102401 thành công lên Supabase và xác nhận đủ 4 cột mới. Backend đang chạy 8080; Swagger và GET guides/events trả 200. Pool local đặt max=3/minIdle=1 để tránh chạm giới hạn 15 kết nối session của DB nhóm. Các ghi chú “chưa áp dụng” bên dưới là trạng thái ở mốc kiểm thử trước lần cập nhật này.

Nhánh `feature/guide-profile`, module member 4. Không sửa Account, Schedule hoặc Payment.

## Database và triển khai

Migration Flyway: `V2026102401__guide_profile.sql` thêm bốn cột vào `tour_guides`:

| Cột | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `bio` | TEXT, nullable | Giới thiệu, tối đa 5.000 ký tự |
| `languages` | TEXT[], NOT NULL, mặc định `{}` | Tối đa 10 ngôn ngữ, mỗi tên 1–50 ký tự không chỉ khoảng trắng |
| `avatar_url` | TEXT, nullable | URL HTTPS công khai từ Cloudinary |
| `avatar_public_id` | TEXT, nullable | ID để quản lý tài sản, không trả qua API |

`guide_id` vẫn là PK, `user_id` vẫn tham chiếu tài khoản theo contract cũ. Không đổi enum/trạng thái/`GuideReference`, không thêm bảng hoặc sửa module khác. Guide cũ được giữ nguyên ID và có bio/ảnh null, ngôn ngữ rỗng.

Phải áp dụng migration trước khi chạy backend mới trên DB dùng chung. Khi triển khai theo Flyway, bật `FLYWAY_ENABLED=true` và để backend chạy migration; không chỉ sửa entity hoặc tắt schema validation. Không tự chạy DDL thủ công rồi để Flyway chạy lại cùng file. Lần triển khai này chỉ kiểm chứng trên PostgreSQL local, chưa sửa Supabase.

## Tạo và sửa hồ sơ

ADMIN gọi `POST /api/admin/guides` với JSON sau, thay `userId` bằng ID tài khoản ACTIVE có sẵn:

```json
{
  "userId": 1,
  "experienceYears": 3,
  "specialization": "Trekking và camping",
  "bio": "Tôi có kinh nghiệm dẫn đoàn trekking và hỗ trợ khách mới.",
  "languages": ["Tiếng Việt", "English"]
}
```

`PUT /api/admin/guides/{guideId}` nhận các trường tương tự nhưng không nhận `userId` để đổi tài khoản. Bio và ngôn ngữ được trim, ngôn ngữ trùng sau trim chỉ giữ một mục; phân biệt hoa/thường. Hai trường mới tùy chọn để request cũ tiếp tục hoạt động. PUT là thay thế hồ sơ: bỏ bio/languages sẽ đưa chúng về null/[]; không tác động ảnh.

Không nhận avatarUrl/publicId do client tự khai báo. Upload file qua endpoint riêng sau khi tạo hồ sơ.

## Thêm, thay và xóa ảnh

- `PUT /api/admin/guides/{guideId}/avatar`: multipart `file`, JPEG/PNG/WebP tối đa 5 MiB; trả 200 kèm hồ sơ có `avatarUrl`.
- `DELETE /api/admin/guides/{guideId}/avatar`: trả 204; gọi lại khi không có ảnh vẫn thành công.
- Cả hai yêu cầu ADMIN; dùng JWT Bearer theo cấu hình hiện có.

Dùng chung `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` với ảnh event; ảnh guide được lưu trong `terrapeak/guides/`. Key cần quyền upload/xóa trong thư mục này; live Cloudinary chưa được gọi trong các test tự động.

Thay ảnh khóa hồ sơ trước khi upload; chỉ xóa ảnh cũ sau khi DB commit. Nếu DB rollback, callback xóa ảnh mới. Nếu dọn ảnh cũ sau commit lỗi, hồ sơ mới vẫn thành công và publicId cần dọn được ghi log. Cloudinary và DB không có giao dịch nguyên tử: khi DELETE ảnh thành công nhưng DB commit thất bại, thử DELETE lại để dọn metadata; nếu callback cleanup lỗi cần dọn theo publicId trong log.

## Xem công khai

`GET /api/guides` và `GET /api/guides/{guideId}` trả thêm `bio`, `languages`, `avatarUrl`, không trả userId/email/phone/avatarPublicId. Vẫn chỉ công khai hồ sơ ACTIVE với tài khoản ACTIVE. Tên hiển thị lấy từ Account, không lưu trùng.

Luồng Swagger: đăng nhập ADMIN → Authorize → tạo hồ sơ → copy guideId → PUT avatar chọn file → GET chi tiết công khai → PUT thay ảnh hoặc DELETE ảnh.

## Kiểm chứng

Dùng database PostgreSQL local riêng, không dùng DB nhóm. Kiểm tra API tạo/sửa/validation, phân quyền, avatar upload/delete và lỗi upstream; rollback/commit cleanup được mô phỏng, HTTP Cloudinary được mock. Bộ test cũ tiếp tục kiểm tra tour, lịch, participant và các module hiện có.

Kết quả: `compileJava` và **130 tests PASS** (9 test mới). Migration trên DB trống qua Flyway PASS; nâng cấp một guide có sẵn giữ nguyên dữ liệu và gán mặc định đúng, đã rollback kiểm tra nâng cấp. Không gọi Cloudinary thật hoặc áp dụng migration lên Supabase.


## Tạo mới tài khoản cùng hồ sơ guide

Luồng mặc định trên frontend đã đổi: ADMIN nhập họ tên, email, điện thoại và thông tin nghề nghiệp; không nhập userId hoặc guideId.

`POST /api/admin/guides/with-account` (ADMIN) nhận:

```json
{
  "fullName": "Nguyễn Văn An",
  "email": "guide.demo@example.com",
  "phone": "0900000000",
  "experienceYears": 3,
  "specialization": "Trekking",
  "bio": "Hướng dẫn viên trekking và camping.",
  "languages": ["Tiếng Việt", "English"]
}
```

Service thuộc tour gọi `UserService.createInternalUser` hiện có với role luôn GUIDE, rồi tạo hồ sơ bằng userId tự sinh trong cùng giao dịch. Không sửa source module Account, không tự tạo role/user bằng SQL. Trùng email bị từ chối, không tự gắn hồ sơ vào một tài khoản khác. Nếu ghi hồ sơ lỗi, dữ liệu user mới cũng rollback.

Response 201: `data.guide` chứa hồ sơ và hai ID, `data.email`, `data.temporaryPassword`, `data.emailSent`. Response có `Cache-Control: no-store`. Không trả password qua GET guide. Frontend chỉ giữ thông tin cấp tài khoản trong state, không ghi local/session storage; ADMIN có thể đóng phần thông tin sau khi ghi nhận. Khi email gửi thất bại, tài khoản và hồ sơ vẫn được tạo, ADMIN có mật khẩu tạm để bàn giao. Sau đó upload ảnh bằng API avatar đã có.

Gửi email dùng nguyên cơ chế của member 1. Giới hạn hiện tại: service Account gửi mail trước khi giao dịch DB commit; email không thể rollback nếu bước ghi guide/commit sau đó thất bại. Bài test mock EmailService để không gửi thư thật. Muốn gửi mail chắc chắn sau commit cần thay đổi contract của module Account, chưa thực hiện theo rule phạm vi.

API cũ `POST /api/admin/guides` dùng userId vẫn giữ để liên kết tài khoản đã tồn tại. Không có migration bổ sung ngoài V2026102401.

Frontend: nhánh `feature/guide-account-registration` trong repo `travel-with-me-Frontend`; chỉ sửa phần guide trong `EventsGuidesAdminPage.tsx` và contract `eventGuideApi.ts`, giữ các thay đổi tour có sẵn. Backend: nhánh `feature/guide-profile`.

Kiểm chứng sau nối tài khoản: compileJava + **135 tests PASS**; 5 test mới kiểm tra tạo role GUIDE, mật khẩu hash, email trùng, validation/quyền và rollback user khi ghi guide lỗi. Frontend `npm run build` và `npm run lint` PASS. Chưa gọi API tạo tài khoản trên DB chung, chưa gửi email thật, chưa áp dụng migration Supabase hoặc restart backend đang chạy.

Cập nhật main ngày 2026-10-24: BE `2fa8f62`, FE `a4a6a02`, giữ các thay đổi local; không conflict. compileJava + 136 tests PASS; FE build/lint PASS. Backup thay đổi trước pull được giữ trong stash ở mỗi repo. Backend đang chạy chưa restart theo main mới.


## Chuẩn hóa cấu trúc 2026-10-24

Nhánh hiện tại: `feature/tour-guide-structure`, từ main `3df868f`. Các endpoint, dữ liệu hồ sơ, migration và luồng cấp tài khoản/ảnh giữ nguyên.

- `GuideService`, `GuideRegistrationService` là interface tại `tour/service`; implementation tại `tour/service/impl` dùng Lombok constructor injection.
- DTO tách từng record trong `tour/dto/request` và `tour/dto/response`; không còn lớp gom `TourDtos`.
- `GuideStatus` chuyển `tour/enums`; lỗi/handler chuyển `tour/exception`; query tài khoản ở `tour/repository/AccountReadRepository`.
- `CloudinaryImageStorage` chuyển `event/storage`, `ImageValidator` ở `event/validator`; không thêm interface/impl cho helper không chứa nghiệp vụ công khai.
- Guide cũ giữ nguyên ID/userId/status và dữ liệu profile. Không chạy/sửa migration Supabase trong bước chuẩn hóa.
- `GuideRegistrationResponse.temporaryPassword` là contract đã có cho ADMIN cấp tài khoản một lần; giữ nguyên để không phá FE, không dùng trong public GET. Đây là ngoại lệ có chủ đích cho quy tắc DTO không chứa mật khẩu tài khoản.

Kết quả: compileJava + compileTestJava + 147 tests PASS trên PostgreSQL local riêng. Frontend không sửa source trong bước này; chỉ giữ thay đổi có sẵn và kiểm tra tương thích. Backend đang chạy không restart tự động theo refactor.

Frontend `npm run lint` và `npm run build` đều PASS sau khi kiểm tra tương thích. Khi chạy backend mới sau đổi package, rebuild output IDE/Gradle để loại class cũ; không bật bean overriding.
