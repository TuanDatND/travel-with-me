# Order integration — cập nhật 2026-10-22

## Service structure — 2026-10-24

Lombok: controller/service/reader/repository dùng @RequiredArgsConstructor cho dependency final. Entity dùng @Getter, @NoArgsConstructor(PROTECTED) và @Setter riêng status; giữ constructor nghiệp vụ và helper hai chiều. DTO record giữ nguyên, không sinh equals/hashCode/toString kiểu @Data trên entity JPA. compileJava và 9 tests Order riêng PASS sau thay đổi; cấu hình test tạm đã dọn.

Luồng đọc thanh toán: OrderServiceImpl → OrderPaymentReader → OrderPaymentRepository → database. SQL và JdbcTemplate chỉ nằm trong repository; OrderPaymentBalance là snapshot trong dto, chứa các phép tính số dư/status giữ nguyên. Reader không giữ SQL hoặc truy cập database trực tiếp. Không sửa module Payment và không thay đổi API/schema.

Refactor reader/repository: compileJava và 9 tests Order riêng PASS; cấu hình test tạm đã dọn. Lỗi compile test Inventory thiếu RentalBookingService vẫn nằm ngoài phạm vi thay đổi.

`modules/order/service/OrderService.java` là interface công khai; `modules/order/service/impl/OrderServiceImpl.java` chứa implementation và @Transactional. Controller và module tích hợp inject OrderService; không phụ thuộc trực tiếp implementation. OrderPaymentReader vẫn là component đọc dữ liệu, không tách interface riêng. Method signature, endpoint và nghiệp vụ giữ nguyên.

Kiểm tra refactor: compileJava PASS; 9 tests Order PASS khi compile riêng test Order bằng Gradle init tạm (đã xóa). Compile toàn bộ test hiện bị lỗi tại Inventory/CatalogIntegrationTests do thiếu RentalBookingService; chưa chỉnh module Inventory.

## Frontend payment entry — 2026-10-24

Đặt trực tiếp ở gear hoặc checkout giỏ thành công có nút Continue to payment tới /payment/pending?orderId=ID. My Orders có Pay now dùng cùng URL. Router đã nối /payment và /payment/:result tới PaymentResultPage của member Payment, bảo vệ đăng nhập và giữ returnTo. Không tự tạo giao dịch khi đặt đơn; người dùng tiếp tục bấm thanh toán trên trang Payment. Không sửa code provider/callback. TypeScript/build PASS; chưa kiểm thử giao dịch thật. Phần Payment hiện có chịu trách nhiệm truy vấn backend và xác minh kết quả, query URL không tự đánh dấu đã trả tiền.

Entity/ID và contract đọc của Order đã `READY_TO_MAP` cho member Payment. Luồng: tạo đơn PENDING → thanh toán đủ (đơn vẫn PENDING) → ADMIN duyệt CONFIRMED → bàn giao → nhận trả nếu thuê. Trạng thái thanh toán được tính riêng từ transactions, không thêm PAID vào OrderStatus.

Frontend: `/gear/:id` chọn mua/thuê trực tiếp hoặc thêm giỏ; `/cart` gửi nhiều sản phẩm của một chi nhánh; `/orders` lịch sử thật và hủy pending; `/admin/orders` xác nhận thủ công, bàn giao, nhận trả. Dùng JWT hiện có của catalog; cần đăng nhập tại `/login`.

## API

- `POST /api/orders`: branchId + items [{productId, itemType: SALE/RENTAL, quantity, rentalStartDate?, rentalEndDate?}]. User lấy từ JWT. Giá snapshot từ Product, tổng server tính, thuê bao gồm hai đầu ngày. Các field giá/trạng thái/ngày đặt cũ trong DTO chỉ giữ tương thích; server bỏ qua.
- `GET /api/orders/me`: đơn của người đăng nhập.
- `GET /api/orders/{id}`: chi tiết đơn thuộc người đăng nhập.
- `POST /api/orders/{id}/cancel`: chủ đơn hủy PENDING chỉ khi chưa có PAYMENT SUCCEEDED và không có PAYMENT PENDING; giải phóng đặt thuê thông qua trạng thái. Đơn đã thu tiền cần quy trình hủy/hoàn tiền riêng.
- `GET /api/orders?userId=...`: ADMIN xem toàn bộ hoặc lọc khách.
- `POST /api/orders/{id}/confirm`: ADMIN duyệt PENDING → CONFIRMED chỉ khi tổng PAYMENT SUCCEEDED trừ REFUND SUCCEEDED >= totalAmount.
- `POST /api/orders/{id}/hand-over`: ADMIN xuất bán hoặc giao thuê qua InventoryService; đơn mua hoàn tất, đơn có thuê IN_PROGRESS.
- `POST /api/orders/{id}/return`: ADMIN nhận trả thuê qua InventoryService; COMPLETED.
- DELETE được giữ tương thích nhưng thực hiện hủy pending; PUT hiện trả 400, cần hủy và đặt lại để thay đổi chi tiết.

## Contract

Order → User/Branch LAZY; OrderItem → Product LAZY chỉ đọc trên FK product_id, giữ productId cho contract cũ. Không đổi schema/migration.
Tạo đơn khóa Product theo ID tăng dần rồi kiểm tra availability để tuần tự hóa đặt thuê cùng sản phẩm; khóa Order khi chuyển trạng thái tránh xuất/nhận trả lặp. Mỗi đơn một dòng/sản phẩm; giỏ có thể chứa nhiều sản phẩm cùng chi nhánh. Pending/confirmed thuê giữ chỗ theo InventoryService.

## Giới hạn hiện tại

- Order chỉ đọc bảng transactions; không thay đổi code Payment hoặc gọi ZaloPay. Payment thành công không tự confirm đơn; admin duyệt riêng. Payment vẫn phải tự xác minh amount, chủ đơn và callback.
- Vận hành chỉ ADMIN; STAFF chưa được mở cho đến khi ghép kiểm tra chi nhánh.
- Hàng mua kiểm tra tồn khi tạo và xuất lại khi bàn giao, chưa giữ hàng mua; có thể bị từ chối bàn giao nếu tồn thay đổi. Pending thuê chưa có hết hạn tự động.
- Các trang demo custody/return-pass cũ chưa đại diện cho đơn thật.
- Kiểm tra hiện tại: unit service, compileJava, frontend TypeScript/build. Chưa chạy luồng HTTP trên database thật.

## Payment contract — 2026-10-22

- OrderPaymentReader thuộc module Order chỉ SELECT transactions theo order_id, participant_id NULL. Chỉ SUCCEEDED ảnh hưởng số dư; PENDING/FAILED/CANCELLED không được tính là đã trả tiền.
- OrderResponse bổ sung paymentStatus (UNPAID/PARTIALLY_PAID/PAID/REFUNDED), paidAmount (tiền thu thành công trừ hoàn thành công, tối thiểu 0), remainingAmount, canApprove, canCancel. Tổng bằng 0 không yêu cầu thu tiền. Không đổi trạng thái Order, bảng hay migration.
- canApprove chỉ true khi PENDING và đủ tiền. Backend kiểm tra lại khi confirm và hand-over; FE không phải nguồn xác thực.
- canCancel chỉ true khi PENDING, chưa từng có PAYMENT SUCCEEDED và không có PAYMENT PENDING. Đơn đã thu dù hoàn lại hết vẫn phải đi qua quy trình hủy/hoàn tiền riêng.
- Member Payment cần lấy amount từ totalAmount của Order, kiểm tra owner/trạng thái PENDING, ghi transaction thành công sau xác minh provider. Không gọi confirm tự động, không thêm endpoint cho client tự đánh dấu đã trả tiền.
- Khi nối Payment, tạo payment/callback/refund cần dùng cùng khóa hàng orders với các thao tác hủy/duyệt để tránh callback đồng thời với hủy hoặc hoàn tiền đồng thời với duyệt. Hiện chỉ Order khóa hàng này; chưa thể bảo đảm phối hợp concurrency với Payment chưa sửa. Callback phải idempotent.
- FE hiển thị trạng thái, vô hiệu hóa duyệt/hủy dựa trên canApprove/canCancel; bấm Tải lại/Đồng bộ sau thanh toán để cập nhật. Không triển khai nút trả tiền hoặc API provider trong thay đổi này.

### Dữ liệu trả thêm trong OrderResponse

| Field | Ý nghĩa |
| --- | --- |
| paymentStatus | UNPAID: chưa thu; PARTIALLY_PAID: số dư > 0 nhưng chưa đủ; PAID: số dư đủ tổng; REFUNDED: từng thu nhưng số dư về 0. Đơn tổng 0 là PAID. |
| paidAmount | max(PAYMENT SUCCEEDED − REFUND SUCCEEDED, 0). Không phải tổng tiền thu trước hoàn. |
| remainingAmount | max(totalAmount − paidAmount, 0). |
| canApprove | Đơn PENDING và số dư thanh toán đủ. Đây là điều kiện nghiệp vụ; caller vẫn phải có quyền ADMIN. |
| canCancel | Đơn PENDING, chưa từng thu thành công, không có PAYMENT đang chờ. Endpoint vẫn kiểm tra chủ đơn. |

Ví dụ phần thanh toán của đơn tổng 200000 đã thanh toán đủ nhưng chưa được duyệt:

```json
{
  "status": "PENDING",
  "totalAmount": 200000,
  "paymentStatus": "PAID",
  "paidAmount": 200000,
  "remainingAmount": 0,
  "canApprove": true,
  "canCancel": false
}
```

### Checklist bàn giao cho member Payment

1. Đọc Order bằng orderId, kiểm tra caller là chủ đơn và đơn còn PENDING; lấy totalAmount từ server.
2. Khóa cùng hàng orders khi tạo giao dịch và xử lý kết quả/callback/refund để phối hợp với hủy/duyệt của Order. Cần thống nhất thứ tự khóa trước khi tích hợp.
3. Xác minh chữ ký/provider, đúng đơn và đúng số tiền; cập nhật transaction idempotent. Không tự chuyển Order sang CONFIRMED.
4. FE tải lại đơn sau khi thanh toán; admin chỉ duyệt khi canApprove = true. Không tin redirect trình duyệt là bằng chứng đã trả tiền.
5. Nếu cần hủy đơn đã thu tiền, thống nhất luồng hoàn tiền và giải phóng đơn; Order chưa có endpoint tự hoàn tiền.

### Kiểm tra đã thực hiện

- `compileJava` và 8 tests `OrderServiceTests` PASS: giá/trạng thái server, ngày thuê thiếu, thiếu lượng thuê, giao/trả lặp, chưa trả/thiếu tiền không duyệt, đủ tiền vẫn chờ admin, đã thu/đang trả không hủy thường, hoàn tiền làm giảm số dư.
- Frontend `npm run lint` và `npm run build` PASS.
- Chưa test HTTP/provider thực tế, callback đồng thời với hủy, hoặc refund đồng thời với duyệt. Module Payment không bị chỉnh sửa trong mốc này.
