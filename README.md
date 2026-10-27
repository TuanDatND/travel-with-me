# TreekTour

## Thiết bị và tồn kho

 chạy `npm ci` rồi `npm run dev`.
Mở `http://localhost:3000/shop` hoặc `/rent`; đăng nhập `/login`;
quản lý `/admin/catalog`; thêm/sửa `/admin/catalog/new`, `/admin/catalog/:id/edit`;
tồn kho `/admin/inventory` hoặc `/staff/inventory`; chi tiết `/gear/:id`.
Frontend dùng Vite proxy `/api` tới backend; backend chỉ phục vụ API, đã gỡ các trang demo HTML.
Tài liệu API và đầu nối Order: [doc/PRODUCT_INVENTORY.md](doc/PRODUCT_INVENTORY.md).
Request mẫu: [doc/product-inventory.http](doc/product-inventory.http).

## Chạy local bằng IntelliJ IDEA

1. Mở project và đồng bộ Gradle.
2. Khởi động PostgreSQL nếu vừa khởi động lại máy:
   `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local-database.ps1`
3. Chọn cấu hình **TreekTour Local** rồi bấm **Run**.
4. Mở http://localhost:8080/swagger-ui/index.html để thử API.

Máy hiện tại dùng JDK 26 tại `C:/Users/dinhv/.jdks/openjdk-26.0.2`.
File `gradle.properties` local đặt `javaVersion=26`; mặc định project vẫn dùng Java 25.
PostgreSQL 17.11 portable nằm trong `.local/pgsql`, dữ liệu trong `.local/pgdata`.
Database `treektour` chạy ở `127.0.0.1:5432`; thông tin kết nối nằm trong `.env`.
Các file cấu hình máy và dữ liệu local được bỏ qua bởi Git.

Tài khoản admin được migration tạo sẵn: `admin@gmail.com` / `admin123`.
Google OAuth, email và upload Cloudinary cần thông tin dịch vụ thật trong `.env` để sử dụng.

## Thiết lập trên máy khác

Cài JDK 25 (hoặc ghi `javaVersion=26` vào `gradle.properties` nếu dùng JDK 26),
cài PostgreSQL, tạo database `treektour`, sao chép `.env.example` thành `.env`
và cập nhật thông tin kết nối. Sau đó chạy `./gradlew bootRun`.

Swagger dùng springdoc 3.x để tương thích Spring Boot 4:
https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot
