# Thiết bị và tồn kho — Member 2

## Phạm vi

- Web desktop chuyển sang repo `AnhND81225/travel-with-me-Frontend`, module `src/features/equipment`. Routes: `/shop`, `/rent`, `/catalog`, `/gear/:id`, `/admin/catalog`, `/admin/catalog/new`, `/admin/catalog/:id/edit`, `/admin/inventory`, `/staff/inventory`, `/login`. Backend đã gỡ trang HTML và assets demo, giữ nguyên API/schema/ID. Vite proxy `/api` đến localhost:8080 khi phát triển; production cần reverse proxy tương đương.
- Đăng nhập qua API Account hiện có. ADMIN tạo/sửa/kích hoạt/ngừng thiết bị; ADMIN/STAFF nhập, kiểm kê, tạm dừng kho. STAFF phải có hồ sơ ACTIVE ở đúng chi nhánh ACTIVE.
- Dùng `ApiResponse(status,message,data,timestamp)`, JWT Bearer và các authority `ADMIN`, `STAFF`, `CUSTOMER` hiện có. Tài khoản không ACTIVE không được xác thực tại API module này.
- Giao diện giữ JWT trong sessionStorage của tab để chuyển trang/tải lại không mất đăng nhập; không lưu mật khẩu hoặc token vào localStorage. Đăng xuất xóa JWT, đóng tab kết thúc phiên phía trình duyệt. Mỗi trang quản lý tải lại hồ sơ `/api/users/me`; backend tiếp tục kiểm tra JWT/role/chi nhánh cho từng API. Return URL chỉ cho phép các trang nội bộ của module.

## Database và quan hệ

Không thêm hoặc sửa migration: hai bảng đã có trong `V1__init.sql`.

| Bảng | Khóa | Dữ liệu và ràng buộc |
| --- | --- | --- |
| `products` | PK `product_id` | Tên tối đa 150; danh mục 100; giá NUMERIC(18,2), không âm; ít nhất một giá khác null; ACTIVE/INACTIVE |
| `inventory` | PK `inventory_id`; FK `branch_id` → branches, `product_id` → products | Unique(branch_id,product_id); total/available/rented không âm; available + rented <= total; ACTIVE/INACTIVE |

Entity Inventory giữ branchId/productId dạng Long; không thay mapping các module khác. Không xóa cứng sản phẩm để bảo toàn FK và lịch sử đơn. Ngừng thiết bị bằng status.

`quantity_total - quantity_available - quantity_rented` là số tạm giữ/hỏng/chưa sẵn sàng. Không ép ba số phải cộng bằng nhau. Kiểm kê chỉ nhập total và available; không được ghi đè rented.

## API

| Phương thức | Đường dẫn | Quyền / nội dung |
| --- | --- | --- |
| GET | `/api/products` | Public; chỉ ACTIVE |
| GET | `/api/products/{id}` | Public; inactive trả 404 |
| GET | `/api/admin/products` | ADMIN; tất cả trạng thái hoặc lọc status |
| GET | `/api/admin/products/{id}` | ADMIN |
| POST | `/api/admin/products` | ADMIN; tạo, trả 201 |
| PUT | `/api/admin/products/{id}` | ADMIN; thay nội dung và giá |
| PATCH | `/api/admin/products/{id}/status` | ADMIN; `{"status":"INACTIVE"}` hoặc ACTIVE |
| GET | `/api/inventory/availability` | Public; branchId, productId, startDate, endDate bắt buộc |
| GET | `/api/inventory/branches/{branchId}` | ADMIN hoặc STAFF đúng chi nhánh; page/size |
| POST | `/api/inventory/branches/{branchId}/products/{productId}/import` | Nhập thêm `{"quantity":10}`; tạo dòng kho nếu chưa có |
| PUT | `/api/inventory/branches/{branchId}/products/{productId}` | Kiểm kê `{"quantityTotal":10,"quantityAvailable":7}` |
| PATCH | `/api/inventory/branches/{branchId}/products/{productId}/status` | Tạm dừng/mở lại kho bằng ACTIVE/INACTIVE |

Danh sách sản phẩm nhận `keyword`, `category` (không phân biệt hoa/thường), `mode=SALE|RENTAL`, `minPrice`, `maxPrice`, `page=0`, `size=20` (1–100). Khi lọc giá phải chọn mode để xác định đúng cột giá. Giá bằng 0 hợp lệ; null nghĩa là không hỗ trợ hình thức đó.

Body tạo/sửa thiết bị:

```json
{"name":"Lều 4 người","category":"Camping","salePrice":1500000,"rentalPricePerDay":150000}
```

Lỗi: 400 validation/tham số; 401 chưa đăng nhập/token không hợp lệ; 403 không đúng quyền/chi nhánh; 404 không tìm thấy; 409 không đủ tồn hoặc vi phạm nghiệp vụ/ràng buộc.

## Đầu nối với Member 3 (Order)

`InventoryService` có ba hàm nội bộ, không mở thành API để khách tùy ý sửa kho:

- `fulfillSale(branchId, productId, quantity)`: giảm total và available khi giao bán.
- `handOutRental(branchId, productId, quantity)`: giảm available, tăng rented khi giao thuê.
- `receiveRental(branchId, productId, quantity)`: tăng available, giảm rented khi nhận trả; vẫn cho trả khi thiết bị/kho/chi nhánh đã inactive.

Các hàm tham gia transaction của bên gọi. Khóa theo thứ tự product → inventory; tăng/giảm chỉ thực hiện khi đủ lượng; lượt nhập đầu đồng thời được tuần tự hóa qua khóa product. Các thao tác nhiều sản phẩm cần khóa theo productId tăng dần để hạn chế deadlock.

**Order chưa được sửa để gọi các hàm này.** Khi nối, Member 3 phải khóa đơn, kiểm tra chuyển trạng thái hợp lệ, cập nhật trạng thái và kho trong cùng transaction. Mỗi lần giao/trả chỉ gọi một lần; gọi service lặp sẽ thay đổi kho lặp. Các hàm không tự suy ra orderId hoặc tự chống callback trùng.

## Lượng thuê theo ngày

Ngày tính bao gồm cả ngày đầu và cuối, từ hôm nay trở đi. Nguồn đặt trước là `orders` + `order_items` đọc qua JDBC, không sửa module Order:

- Bỏ đơn CANCELLED/COMPLETED và dòng không phải RENTAL.
- Dòng PENDING/CONFIRMED tính giữ chỗ. Không có tự động hết hạn PENDING; Order cần hủy/hết hạn để giải phóng.
- Tính mức đặt cao nhất tại cùng một ngày bằng các mốc tăng/giảm, không cộng tất cả đơn chỉ vì cùng giao với khoảng tra cứu.
- RENTED đã bị trừ trong available bởi service giao thuê, nên không trừ lần hai. Không cộng trước số thiết bị đang thuê dự kiến trả trong tương lai: kết quả mang tính bảo thủ.
- Kết quả là `max(0, quantity_available - peakReserved)`. Kho inactive hoặc chưa có dòng kho trả 0; sản phẩm/chi nhánh inactive không được dùng để thuê.

**Tra cứu availability không giữ chỗ.** Khi Order nhận đặt thuê, cần tuần tự hóa bước kiểm tra và lưu đơn bằng cùng khóa product/inventory, trong cùng transaction. Nếu Order tiếp tục tạo đơn độc lập như hiện tại, API tra cứu không thể bảo đảm chống đặt vượt số lượng tương lai. Việc trừ kho khi giao cũng phụ thuộc Order cập nhật trạng thái nhất quán.

## Kiểm thử và thao tác mẫu

`CatalogIntegrationTests` kiểm tra sản phẩm, validation, JWT inactive, quyền STAFF theo chi nhánh, stock lifecycle, tính lịch thuê và hai yêu cầu đồng thời bằng PostgreSQL thật. Dùng database thử riêng:

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/treektour_catalog_test'
.\gradlew.bat compileJava test
```

Database thử phải được tạo trước; username/password lấy từ `.env` hoặc biến môi trường. Không chạy test tích hợp trên database dùng chung của nhóm.

Request mẫu ở `doc/product-inventory.http`. Cần chi nhánh ACTIVE từ module Branch và tài khoản admin/nhân viên hiện có. Không thêm dữ liệu mẫu vào migration chung.
