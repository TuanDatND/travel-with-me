# Test tích hợp Tour + Account bằng JWT

Nhánh: `feature/tour-auth-integration`. Chỉ test local; chưa push hoặc merge các thay đổi tích hợp. Đã cập nhật main tới `eabe66f` (PR #9 của member 1), rồi tạo nhánh này trước khi sửa. Code các member khác giữ nguyên.

## Môi trường đã kiểm tra

- PostgreSQL riêng tại `127.0.0.1:55439`, database `tour_auth_current`, user `tour_test`. Không dùng DB nhóm ở cổng 5432.
- Backend demo tại `http://127.0.0.1:8081`.
- Tài khoản mẫu chỉ trong DB test: `tour-admin@example.test` / `tour-demo-pass`, quyền `ADMIN`.
- Đã chạy 19 tests thành công và gọi HTTP thật: login 200, public 200, admin chưa đăng nhập 401, admin có JWT 200, tạo nháp 201.
- Cloudinary chưa gọi thật. ZaloPay dùng giá trị giả để khởi động, không dùng test thanh toán.

## Khởi động lại khi cần

Chạy từ thư mục gốc repository. Bỏ qua bước khởi động nếu dịch vụ đang chạy.

```bash
pg_ctl -D build/tour-test-postgres/data \
  -l build/tour-test-postgres/server.log \
  -o "-p 55439 -h 127.0.0.1 -k '$PWD/build/tour-test-postgres/socket'" -w start

DB_URL=jdbc:postgresql://127.0.0.1:55439/tour_auth_current \
DB_USERNAME=tour_test DB_PASSWORD= \
SERVER_PORT=8081 SERVER_ADDRESS=127.0.0.1 \
ZALOPAY_APP_ID=1 ZALOPAY_KEY1=test-only ZALOPAY_KEY2=test-only \
ZALOPAY_CREATE_URL=http://127.0.0.1:1/create \
ZALOPAY_QUERY_URL=http://127.0.0.1:1/query \
ZALOPAY_CALLBACK_URL=http://127.0.0.1:8081/callback \
bash gradlew bootRun
```

## Gọi API từ terminal khác

Đăng nhập và lấy token, không cần cookie/CSRF:

```bash
TOKEN=$(curl -fsS http://127.0.0.1:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"tour-admin@example.test","password":"tour-demo-pass"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')

curl -i http://127.0.0.1:8081/api/events

curl -i http://127.0.0.1:8081/api/admin/events \
  -H "Authorization: Bearer $TOKEN"

curl -i -X POST http://127.0.0.1:8081/api/admin/events \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"eventName":"Tour test JWT","eventType":"TREKKING","basePrice":1500000}'
```

Lệnh cuối tạo dữ liệu thật trong DB test, trả 201; lấy `data.eventId` để xem chi tiết admin. Tour nháp chưa xuất hiện trong danh sách public. Đăng ký mới qua `/api/auth/register` tạo `CUSTOMER`, không có quyền quản trị. Admin mẫu được cấp quyền bằng fixture SQL riêng trong DB test, không có API tự nâng quyền.

Đối với máy khác, tạo DB trống riêng và để Flyway chạy V1, V3 rồi migration tour. Tài khoản demo không tự xuất hiện: đăng ký tài khoản test, nhờ người quản trị DB cấp role admin. Không chạy V1 lên schema đã có bảng.

## Khi chạy IntelliJ với Supabase

`.env` local hiện cấu hình Supabase, bật Flyway và kiểm tra checksum, tắt baseline tự động. V1/V3 đã được đối chiếu checksum khớp với code mới bằng truy vấn chỉ đọc. Khởi động IntelliJ với `.env` sẽ chạy migration tour còn thiếu trên DB chung; chưa thực hiện bước đó trong lần kiểm tra này. Không dùng tài khoản admin demo local để đăng nhập Supabase.

Sáu biến ZALOPAY_* trong `.env` là cấu hình giả, endpoint trỏ localhost cổng 1 để không gọi dịch vụ thanh toán thật. Chỉ dùng test tour/auth.

## Phạm vi và việc còn lại

Chỉ chỉnh module tour, tour tests và tài liệu tour. Không sửa entity/repository/service/filter/migration của member 1. Member 1 đã tải role EAGER, tour bỏ transaction/copy principal tạm và dùng authority ADMIN theo DB. Guard trạng thái tài khoản còn giữ trong module tour vì filter chung chưa kiểm tra ACTIVE/BLOCKED; có thể bỏ guard khi member 1 hoàn thiện filter chung. JWT secret chung hiện còn hardcode, cần member 1 chuyển sang cấu hình trước triển khai.

Dừng backend bằng Ctrl+C nếu tự chạy; dừng PostgreSQL test sau khi dừng backend:

```bash
pg_ctl -D build/tour-test-postgres/data -m fast -w stop
```
