# Kiểm thử Tour/Guide trên Swagger — 10/10/2026

Nhánh `feature/tour-auth-integration`. Test UI bằng Swagger tại `http://localhost:8081`, backend IntelliJ kết nối Supabase. Đăng nhập tài khoản do người dùng cung cấp, quyền ADMIN; không lưu mật khẩu/token trong báo cáo. Backend demo local riêng đã được dừng để tránh nhầm địa chỉ IPv4/IPv6.

## Kết quả

- Đã thực hiện 33 request kiểm tra được ghi nhận trên Swagger; 32 khớp kỳ vọng. Một lần dự đoán 404 cho userId không tồn tại nhận 400: đã đối chiếu AccountReader.find và test lại theo contract 400, đạt; đây là sai kỳ vọng test, không sửa code.
- Chạy lại 19 tests tự động trên PostgreSQL local riêng `tour_auth_current`: 0 failures. Cloudinary được mock trong tests tự động; không chạy tests này trên Supabase.
- Chưa xác nhận luồng Cloudinary thật (upload thành công/sửa mô tả/xóa ảnh thành công), công bố tour thành công và kiểm tra ảnh cuối của tour đã công bố trên Swagger. Upload hiện trả 503 STORAGE_NOT_CONFIGURED đúng với cấu hình thiếu credentials. Các luồng này được kiểm tra tự động bằng mock.

## Chi tiết request

| Kiểm tra | HTTP thực tế | Kỳ vọng ban đầu | Kết quả |
| --- | --- | --- | --- |
| Tạo tour | 201 | 201 | Đạt |
| Lọc tour admin | 200 | 200 | Đạt |
| Chi tiết tour admin | 200 | 200 | Đạt |
| Sửa tour | 200 | 200 | Đạt |
| Ẩn tour nháp ở API public | 404 | 404 | Đạt |
| Danh sách tour public | 200 | 200 | Đạt |
| Chặn page âm | 400 | 400 | Đạt |
| Chặn khoảng giá đảo ngược | 400 | 400 | Đạt |
| Validation tên trống và giá âm | 400 | 400 | Đạt |
| Chặn công bố tour chưa có ảnh | 409 | 409 | Đạt |
| Tạo hồ sơ hướng dẫn viên | 201 | 201 | Đạt |
| Chặn trùng tài khoản và chuyên môn | 409 | 409 | Đạt |
| Chi tiết guide admin | 200 | 200 | Đạt |
| Sửa guide | 200 | 200 | Đạt |
| Guide public và bảo vệ thông tin riêng | 200 | 200 | Đạt |
| Danh sách guide admin | 200 | 200 | Đạt |
| Danh sách guide public | 200 | 200 | Đạt |
| Tắt hồ sơ guide | 200 | 200 | Đạt |
| Ẩn guide không hoạt động | 404 | 404 | Đạt |
| Bật lại hồ sơ guide | 200 | 200 | Đạt |
| Upload khi chưa cấu hình Cloudinary | 503 | 503 | Đạt |
| Sửa mô tả ảnh không tồn tại | 404 | 404 | Đạt |
| Xóa ảnh không tồn tại | 404 | 404 | Đạt |
| Chặn guide có tài khoản không tồn tại | 400 | 404 | Đối chiếu contract: 400 đúng; đã test lại |
| Lưu trữ tour test | 200 | 200 | Đạt |
| Chặn sửa tour đã lưu trữ | 409 | 409 | Đạt |
| Chặn userId không tồn tại (contract trả 400) | 400 | 400 | Đạt |
| Ẩn hồ sơ test sau kiểm tra | 200 | 200 | Đạt |
| Kiểm tra tour test đã ARCHIVED | 200 | 200 | Đạt |
| Admin thiếu token | 401 | 401 | Đạt |
| Public không cần đăng nhập | 200 | 200 | Đạt |
| Admin token sai | 401 | 401 | Đạt |
| Khôi phục phiên admin sau kiểm tra | 200 | 200 | Đạt |

## Dữ liệu test để lại

- Tour `eventId=2`, tên `TEST_SWAGGER_1791609055453`, trạng thái ARCHIVED.
- Hồ sơ guide `guideId=1`, chuyên môn `TEST_SWAGGER_1791609055453 trekking`, trạng thái INACTIVE.
- Không sửa tour/guide có sẵn của người dùng, không sửa tài khoản hoặc nâng quyền. Không xóa dữ liệu thật. Swagger đã được gắn lại token admin hợp lệ sau kiểm tra.

Bằng chứng ảnh tại `build/swagger-test-result.jpg`; dữ liệu request đã lược bỏ thông tin đăng nhập tại `build/swagger-results.json`. Cả hai nằm trong build/ được Git bỏ qua.
