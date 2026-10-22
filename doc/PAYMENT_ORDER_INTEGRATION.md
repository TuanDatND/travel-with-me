# Payment → Order (2026-10-23)

Transaction map `order` → Order bằng `@ManyToOne(fetch = LAZY)` qua FK có sẵn
`transactions.order_id` → `orders.order_id`, không cascade. DTO/request/response vẫn dùng
`orderId: Long`; `participantId` trong entity giữ `Long` vì Schedule chưa READY_TO_MAP.
Không đổi schema, migration hoặc code Order. Payment đã bỏ JdbcTemplate, dùng OrderRepository
hiện có và JPQL trong TransactionRepository. TransactionTemplate vẫn giữ để transaction DB
kết thúc trước khi gọi HTTP ZaloPay.
Order đọc giao dịch qua OrderPaymentReader theo contract trong ORDER_INTEGRATION.md.

## Tạo thanh toán đơn

`POST /api/payments/zalopay/`, JWT của chủ đơn ACTIVE:

```json
{"orderId":5,"description":"Thanh toán đơn 5","redirectUrl":"http://localhost:3000/payment/result"}
```

Truyền `redirectUrl` là trang kết quả FE. Backend thêm `transactionId` vào query URL trả về (giữ các tham số khác); FE đọc ID này hoặc lưu ID từ response tạo thanh toán. `amount` và `appUser` không còn bắt buộc cho đơn;
amount hợp lệ cũ nếu gửi vẫn bị bỏ qua, số tiền lấy từ orders.total_amount.
app_user gửi provider là order_<orderId>. Đơn phải PENDING và có tổng nguyên VND > 0.
Đơn tổng 0 không cần gọi ZaloPay; Order cho phép admin duyệt theo contract hiện có.

Chặn tạo thêm khi có PAYMENT PENDING hoặc số tiền thu ròng SUCCEEDED > 0 (409).
Không hỗ trợ thanh toán bù một phần trong mốc này. Khi timeout trả 502 kèm transaction ID;
không bấm tạo lại: dùng query/lịch sử để đối soát giao dịch đã tạo.

## Kết quả

- Luồng local không dùng callback: create không gửi callback_url, đã bỏ cấu hình security riêng cho callback. Đã xóa endpoint callback, DTO và xử lý callback; cấu hình Security chung không thay đổi.
- Query GET `/api/payments/zalopay/query/{transactionId}` yêu cầu JWT chủ đơn.
- Query thành công (return_code=1) và amount khớp mới cập nhật Transaction SUCCEEDED. Order vẫn PENDING, ADMIN duyệt riêng.
- Khóa `orders` trước `transactions`, trong cùng transaction; khóa Order dùng chung với cancel/confirm/hand-over.
- Không giữ khóa DB trong lúc gọi HTTP provider. Giao dịch PENDING được commit trước khi gọi provider.
- Query lặp không ghi thành công lần hai và không hạ SUCCEEDED. Refresh bản ghi sau khi khóa để tránh dữ liệu JPA cũ.
- Query return_code=2/sub_return_code=-54 xác nhận hết hạn → FAILED; return_code=2/sub_return_code=0/is_processing=false → FAILED; return_code=3/đang xử lý/thiếu kết quả giữ PENDING. SUCCEEDED không bị hạ trạng thái. Timeout/null response giữ PENDING, tránh coi kết quả chưa rõ là đã thất bại. Job đối soát tự động mỗi phút; PENDING vẫn chặn hủy/tạo mới đến khi provider xác nhận kết quả.
- Create bị provider từ chối rõ ràng đánh dấu FAILED; transport error giữ PENDING.

## Lịch sử

GET `/api/payments/transactions/history?page=0&size=20` trả ApiResponse với
`data={content,page,size,totalElements,totalPages}`. size 1–100; sort createdAt DESC, id DESC.
Trang vượt cuối trả content rỗng; tham số sai trả 400.
Phạm vi lịch sử vẫn là danh sách toàn hệ thống cho tài khoản đã đăng nhập như code cũ;
chưa triển khai lọc owner/chi nhánh. Không coi đây là API lịch sử cá nhân đã phân quyền.

## Phạm vi chưa tích hợp

Participant giữ contract tạm: nhận amount/appUser từ request; chưa xác minh owner/giá tour
hoặc xác nhận vé. Hoàn tiền, hủy đơn đã thu tiền và báo cáo chưa triển khai trong mốc này.
Query chưa kiểm thử với ZaloPay live; khóa liên module chưa kiểm thử trên PostgreSQL thật.

## FE nhận kết quả local

1. Gọi create với JWT chủ đơn và redirectUrl; nhận transaction_id và mở order_url trong zalopay_response.
2. ZaloPay chuyển về trang kết quả FE. FE đọc transactionId rồi gọi GET query/{transactionId} với JWT.
3. Dựa vào data.status: SUCCEEDED → trang thành công, FAILED/CANCELLED → trang thất bại, PENDING → đang xử lý và query lại sau vài giây (giới hạn số lần, cho phép kiểm tra lại thủ công).
4. Lỗi mạng/502 không phải thanh toán thất bại; hiện chưa xác nhận được và cho thử query lại.
5. Tải lại Order để đọc paymentStatus/canApprove. Không gửi trạng thái thành công từ FE vào backend.

Nếu người dùng đóng trình duyệt trước bước query thì DB có thể còn PENDING; query lại khi mở đơn/lịch sử.
Không bảo đảm ZaloPay sẽ redirect khi người dùng tự đóng trang hoặc hủy; FE cần nút kiểm tra trạng thái.
Cấu hình ZaloPayConfig chung hiện có (bao gồm key2/callbackUrl) giữ nguyên để không sửa code ngoài Payment.

## Kiểm tra mapping JPA

compileJava + 27 tests Payment/Order PASS sau thay JDBC bằng JPA. Tests dùng mock repository/provider,
kiểm tra giá server, quyền chủ đơn, chặn thanh toán trùng, khóa Order trước Transaction,
query idempotent, mismatch amount, processing/failure, lịch sử phân trang và hồi quy Order.
Chưa kiểm chứng mapping/JPQL hoặc khóa đồng thời trên PostgreSQL thật trong mốc này.

## Thất bại/hết hạn và đối soát tự động — 2026-10-23

- Create gửi expire_duration_seconds=900 (15 phút) sang provider. Không thêm EXPIRED vào enum/database; hết hạn được lưu FAILED, lý do -54 nằm trong zalopay_response của query.
- Job chạy sau 30 giây từ lúc khởi động, sau đó mỗi 60 giây; lấy tối đa 50 ID PAYMENT/ZALOPAY/PENDING đã tạo ít nhất 30 giây trước. Duyệt theo cursor để giao dịch cũ không chặn giao dịch mới; lỗi một giao dịch không chặn phần còn lại.
- Job dùng cùng xử lý query, xác minh giá khi thành công, cùng khóa/idempotency. Không yêu cầu JWT của người dùng; không mở API mới.
- Mã query -54 là xác nhận hết hạn. Lỗi query -401/-402/-429/-500/-999, không tồn tại -101 và thiếu số dư -63 không chứng minh giao dịch đã chấm dứt: giữ PENDING và thử lại.
- Không đánh FAILED chỉ vì đủ 15 phút hoặc người dùng đóng/hủy trang: vẫn query provider để không bỏ sót tiền đã thanh toán. Nếu provider liên tục trả PROCESSING thì tiếp tục chờ/đối soát, không tự giải phóng.
- Không áp thời hạn mới ngược lên giao dịch cũ; giao dịch cũ vẫn được query và xử lý theo kết quả provider.
- Cấu hình tùy chọn: payment.reconciliation.enabled=false để tắt job; payment.reconciliation.delay-ms (60000), payment.reconciliation.initial-delay-ms (30000). Không sửa application.yml chung.
- FAILED hết hạn cho phép tạo giao dịch mới nếu đơn còn PENDING và không có giao dịch PENDING khác/số tiền thu ròng thành công.

Nguồn chính thức: [Create Order](https://docs.zalopay.vn/docs/specs/order-create),
[Query Order Status](https://docs.zalopay.vn/docs/specs/order-query),
[Status Codes](https://docs.zalopay.vn/docs/developer-tools/knowledge-base/status-codes).

## Redirect từ Swagger và phiên FE

JWT trong Swagger không tự đăng nhập FE. Trang kết quả cần phiên đăng nhập FE của chủ đơn;
Payment FE báo yêu cầu đăng nhập và giữ đường dẫn kết quả trong login?returnTo=... để quay lại query.
Không chuyển JWT vào redirectUrl hoặc lưu JWT mới vào localStorage.
Query/create trả thêm orderId/participantId để trang kết quả lấy được đơn dù không có mapping sessionStorage.
Payment fetch không follow redirect đăng nhập OAuth; lỗi auth/network hiển thị hướng dẫn kiểm tra hoặc đăng nhập lại.

## Tự phục hồi giao dịch theo đơn

GET /api/payments/zalopay/orders/{orderId}/payment yêu cầu JWT chủ đơn, ưu tiên giao dịch PENDING để query provider; nếu không có pending lấy giao dịch gần nhất; chưa từng thanh toán trả data=null.
FE dùng API này khi vào trang theo orderId; không phụ thuộc mapping sessionStorage, không yêu cầu người dùng nhập transactionId. Nếu create bị 409 do request đồng thời hoặc giao dịch cũ, FE kiểm tra lại đơn/giao dịch. Link đăng nhập chỉ hiện với lỗi 401/403.
Giao dịch provider còn PENDING vẫn chặn tạo lại; không tự chuyển FAILED để bỏ chặn.

## Thanh toán lại cùng Order

FE bật nút Thanh toán lại khi giao dịch còn PENDING. POST /api/payments/zalopay/retry nhận cùng body create (orderId, redirectUrl), chỉ dành cho Order.
Backend kiểm tra chủ đơn/Order PENDING; query lượt cũ trước. Nếu SUCCEEDED trả kết quả cũ, không thu lần nữa. Nếu còn PENDING, gọi /v2/cancel cùng môi trường provider và MAC key1; chỉ ghi CANCELLED khi provider return_code=1. Sau đó gọi luồng create có khóa Order và kiểm tra số dư/giao dịch PENDING một lần nữa. Đơn và tổng tiền không đổi, không tạo Order mới.
Cancel bị từ chối thì query lại để nhận trường hợp thanh toán thắng cuộc đua; còn chưa rõ trả 409, timeout trả 502, không tạo mới. Không dựa vào status redirect để ghi CANCELLED.
Build + 35 tests Payment/Order PASS với mock (gồm 5 ca retry); FE build PASS. Chưa thử API cancel với app sandbox thật hoặc concurrency PostgreSQL.
Contract provider: https://docs.zalopay.vn/docs/specs/order-cancel (API explorer: POST /v2/cancel, MAC app_id|app_trans_id|key1).

## API hủy riêng một lượt thanh toán

POST `/api/payments/zalopay/{transactionId}/cancel`, không body, JWT chủ đơn.
- Kiểm tra giao dịch ZALOPAY/PAYMENT thuộc chủ đơn ACTIVE; tour chưa tích hợp hủy trả 400.
- Đã SUCCEEDED trong DB trả 409 (cần hoàn tiền); FAILED/CANCELLED trả trạng thái hiện tại, không gọi provider lại.
- PENDING query provider trước; nếu đã thành công trả SUCCEEDED để FE báo kết quả, không gọi cancel.
- Chỉ khi provider cancel return_code=1 mới chuyển CANCELLED dưới khóa orders → transactions; không ghi đè SUCCEEDED đồng thời.
- Provider từ chối cancel thì query lại; chưa rõ trả 409, lỗi mạng trả 502 và giữ PENDING.
- Dùng chung xử lý với retry; không tạo giao dịch hoặc đơn mới trong API cancel.
- FE có nút Hủy lượt thanh toán khi PENDING; tạm dừng polling trong khi hủy, hiển thị CANCELLED/thành công/lỗi, cho thanh toán lại nếu đơn chưa trả và còn PENDING.

Kiểm tra compileJava + 40 tests Payment/Order PASS (mock), FE build PASS; chưa thử API cancel trên sandbox thật.
