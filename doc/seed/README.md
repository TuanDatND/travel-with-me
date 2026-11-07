# Bộ dữ liệu demo toàn hệ thống

File: `SEED_DEMO.sql`, PostgreSQL, chạy thủ công sau **toàn bộ migration hiện tại**, gồm `V2026103101`. Không đặt file trong `db/migration` để tránh tự động thêm tài khoản demo trên môi trường khác.

## Chạy

Dừng backend/worker trong lúc nạp để giữ nguyên snapshot demo. Chọn database dev/test đã chạy Flyway rồi dùng:

```powershell
psql -h localhost -p 5432 -U postgres -d treektour -v ON_ERROR_STOP=1 -f doc/seed/SEED_DEMO.sql
psql -h localhost -p 5432 -U postgres -d treektour -v ON_ERROR_STOP=1 -f doc/seed/VERIFY_DEMO.sql
```

Hoặc dán **toàn bộ file** vào SQL Editor của database dev/test và Run. Seed có transaction: lỗi bất kỳ sẽ rollback toàn bộ dữ liệu mới. Không cần extension, không hard-code PK, không reset sequence, không thay migration/schema/API. Chưa chạy file này trên DB chung/Supabase.

`VERIFY_DEMO.sql` kiểm tra snapshot ngay sau seed (trước khi thao tác qua app): số lượng bản ghi, tổng tiền đơn, số ghế, lượng thuê trong kho và số dư đơn đã duyệt. Đã kiểm thử trên PostgreSQL 18 local riêng: áp dụng toàn bộ SQL migration hiện tại, seed lần đầu, seed lần hai và assertions đều PASS; mật khẩu BCrypt xác minh PASS; `gradlew.bat compileJava compileTestJava` PASS. Migration được thực thi trực tiếp bằng psql trong kiểm tra này; không phải kiểm tra startup Flyway/Hibernate hoặc API end-to-end.

## Đăng nhập

Mật khẩu chung: **`Admin@123`**, BCrypt cùng hash của migration V4.

| Vai trò | Email |
| --- | --- |
| ADMIN | `admin@seed.treektour.test` |
| STAFF HCM | `staff.hcm@seed.treektour.test` |
| STAFF Đà Lạt | `staff.dalat@seed.treektour.test` |
| GUIDE | `guide.an@seed.treektour.test`, `guide.binh@seed.treektour.test` |
| CUSTOMER | `customer.lan@seed.treektour.test`, `customer.minh@seed.treektour.test`, `customer.ha@seed.treektour.test` |

Email `.test` chỉ là dữ liệu giả. SQL không gửi mail, không upload Cloudinary, không gọi ZaloPay hoặc chuyển tiền.

## Dữ liệu

- 8 users, 4 roles (tái sử dụng role đã tồn tại), 3 chi nhánh, 2 staff, 2 guides kèm bio/languages.
- 8 sản phẩm: 6 vừa bán vừa thuê, 2 chỉ bán; 24 dòng tồn kho.
- 4 tour (3 PUBLISHED, 1 DRAFT), mỗi tour có details/itinerary/packingItems JSONB; không tạo ảnh hoặc Cloudinary public ID giả.
- 4 lịch: 2 OPEN trong tương lai, 1 COMPLETED trong quá khứ, 1 CANCELLED; lịch hủy dùng tour PUBLISHED. Có history snapshot.
- 4 người đăng ký: 2 CONFIRMED, 1 COMPLETED/CHECKED_IN, 1 CANCELLED đã hoàn tiền; có UUID, hạn giữ chỗ, biên nhận và audit.
- 6 đơn: PENDING chưa trả, PENDING đã trả, CONFIRMED thuê tương lai, IN_PROGRESS đang thuê, COMPLETED mua hàng và CANCELLED đã hoàn tiền. Mỗi đơn có 1 item.
- 11 giao dịch MANUAL/SUCCEEDED: 9 PAYMENT, 2 REFUND, kèm succeeded_at và audit admin/mã chứng từ cho hoàn tiền. Báo cáo dùng các giao dịch này; không có giao dịch chờ đối soát provider.
- Đơn thuê tính 3 ngày bao gồm ngày đầu/cuối; tồn kho trừ đúng 1 hàng bán và 1 hàng đang thuê. Ghế đã hủy không tính vào current_participants.

Thời gian tính theo ngày chạy SQL, múi giờ Việt Nam. Khi xem báo cáo, chọn khoảng **30 ngày gần nhất** để bao gồm dữ liệu lịch sử. `event_images` và `schedule_payment_reviews` để trống vì demo không tạo tài nguyên ảnh hoặc tình huống cần review thanh toán.

## Chạy lại

Admin seed là marker cho cả batch. Chạy lại khi marker tồn tại sẽ **bỏ qua toàn bộ batch**, không thêm trùng và không ghi đè thay đổi qua ứng dụng. Advisory transaction lock tuần tự hóa các lần chạy seed đồng thời. Nếu email seed khác đã tồn tại nhưng thiếu marker, script báo lỗi và rollback.

Seed không khôi phục dữ liệu đã sửa/xóa và không làm mới lịch cũ khi chạy lại. Dùng database dev/test mới nếu cần một snapshot mới; không xóa riêng admin marker để chạy lại trên batch còn dữ liệu. Tài khoản demo có quyền ADMIN và mật khẩu công khai, chỉ nạp vào dev/test.
