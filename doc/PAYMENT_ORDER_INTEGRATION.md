# Payment → Order (2026-10-23)

Transaction map `order` → Order bằng `@ManyToOne(fetch = LAZY)` qua FK có sẵn
`transactions.order_id` → `orders.order_id`, không cascade. DTO/request/response vẫn dùng
`orderId: Long` và `participantId: Long`; entity đã map cả Order và EventParticipant bằng LAZY.
Không đổi schema, migration hoặc code Order. Payment đã bỏ JdbcTemplate, dùng OrderRepository
hiện có và JPQL trong TransactionRepository. TransactionTemplate vẫn giữ để transaction DB
kết thúc trước khi gọi HTTP ZaloPay.
Order đọc giao dịch qua OrderPaymentReader theo contract trong ORDER_INTEGRATION.md.

## Tạo thanh toán đơn

`POST /api/payments/zalopay/`, JWT của chủ đơn ACTIVE:

```json
{"orderId":5,"description":"Thanh toán đơn 5"}
```

Return URL cố định do backend cấu hình qua `PAYMENT_RETURN_URL` trong `.env`, mặc định
`http://localhost:3000/payment/result` (đúng route và cổng dev FE hiện tại). Request không nhận
redirectUrl; field cũ nếu FE gửi sẽ bị bỏ qua. Response có `redirectUrl` là URL cấu hình kèm
`transactionId`; cùng URL này được gửi vào embed_data.redirecturl của ZaloPay. FE đọc ID để query.
`amount` và `appUser` không còn bắt buộc cho đơn;
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

Participant đã tích hợp giá/owner/hạn vé và xác nhận nội bộ; xem mục thanh toán tour bên dưới. Hoàn tiền, hủy đơn đã thu tiền và báo cáo chưa triển khai trong mốc này.
Query chưa kiểm thử với ZaloPay live; khóa liên module chưa kiểm thử trên PostgreSQL thật.

## FE nhận kết quả local

1. Gọi create với JWT chủ đơn; return URL do backend cấu hình; nhận transaction_id và mở order_url trong zalopay_response.
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

FE giữ nhãn nút “Thanh toán”, kể cả khi dùng luồng thanh toán lại cho giao dịch PENDING. POST /api/payments/zalopay/retry nhận cùng body create, nhận đúng một orderId hoặc participantId; URL lấy từ cấu hình backend.
Backend kiểm tra chủ đơn/Order PENDING; query lượt cũ trước. Nếu SUCCEEDED trả kết quả cũ, không thu lần nữa. Nếu còn PENDING, gọi /v2/cancel cùng môi trường provider và MAC key1; chỉ ghi CANCELLED khi provider return_code=1. Sau đó gọi luồng create có khóa Order và kiểm tra số dư/giao dịch PENDING một lần nữa. Đơn và tổng tiền không đổi, không tạo Order mới.
Cancel bị từ chối thì query lại để nhận trường hợp thanh toán thắng cuộc đua; còn chưa rõ trả 409, timeout trả 502, không tạo mới. Không dựa vào status redirect để ghi CANCELLED.
Build + 35 tests Payment/Order PASS với mock (gồm 5 ca retry); FE build PASS. Chưa thử API cancel với app sandbox thật hoặc concurrency PostgreSQL.
Contract provider: https://docs.zalopay.vn/docs/specs/order-cancel (API explorer: POST /v2/cancel, MAC app_id|app_trans_id|key1).

## API hủy riêng một lượt thanh toán

POST `/api/payments/zalopay/{transactionId}/cancel`, không body, JWT chủ đơn.
- Kiểm tra giao dịch ZALOPAY/PAYMENT thuộc chủ đơn ACTIVE; áp dụng cả Order và Participant, chỉ chủ đơn/chủ vé ACTIVE được gọi.
- Đã SUCCEEDED trong DB trả 409 (cần hoàn tiền); FAILED/CANCELLED trả trạng thái hiện tại, không gọi provider lại.
- PENDING query provider trước; nếu đã thành công trả SUCCEEDED để FE báo kết quả, không gọi cancel.
- Chỉ khi provider cancel return_code=1 mới chuyển CANCELLED dưới khóa orders → transactions; không ghi đè SUCCEEDED đồng thời.
- Provider từ chối cancel thì query lại; chưa rõ trả 409, lỗi mạng trả 502 và giữ PENDING.
- Dùng chung xử lý với retry; không tạo giao dịch hoặc đơn mới trong API cancel.
- FE có nút “Hủy thanh toán” cạnh “Kiểm tra lại” khi giao dịch PENDING; tạm dừng polling trong khi hủy, hiển thị CANCELLED/thành công/lỗi, cho thanh toán lại nếu đơn chưa trả và còn PENDING.

Kiểm tra compileJava + 40 tests Payment/Order PASS (mock), FE build PASS; chưa thử API cancel trên sandbox thật.

## Giao diện và điểm nối FE hiện tại — 2026-10-23

- Repo FE: `travel-with-me-Frontend`, nhánh triển khai `feature/transactions`. Các route `/payment/result`, `/payment/success`, `/payment/pending` dùng `UserLayout` (header/footer có sẵn), hiển thị theo kết quả backend; không tin route success hoặc redirect để ghi nhận tiền.
- Nút danh sách đơn và nút bên trong trang đều ghi “Thanh toán”. Nút này gọi create hoặc retry tùy trạng thái giao dịch.
- Trang Payment có “Kiểm tra lại” và “Hủy thanh toán” (chỉ khi PENDING). Hủy chỉ kết thúc giao dịch sau xác nhận provider, giữ nguyên đơn để thanh toán lại; đây không phải hoàn tiền.
- Banner lớn đầu trang đã bỏ. Thông báo lỗi, kết quả hủy và chi tiết thanh toán vẫn hiển thị.
- Polling FE mỗi 5 giây, tối đa 6 lần; chạy nền không bật loading/khóa nút hoặc làm nhảy bố cục. Bỏ qua khi tab ẩn, request còn chạy hoặc người dùng đang thanh toán/hủy; loại phản hồi cũ. Dữ liệu Order không đổi được giữ nguyên để tránh tải lại danh mục không cần thiết.
- Nút “Hủy đơn hàng” trong Đơn của tôi cho phép xử lý đơn PENDING/UNPAID/paidAmount=0: FE đọc lại Order, tìm/query giao dịch; nếu còn PENDING gọi Payment cancel trước; chỉ gọi API Order cancel hiện có khi backend Order trả canCancel=true. Không hủy đơn nếu vừa thu tiền hoặc provider chưa xác nhận. Không sửa code backend Order.
- Helper nối luồng này là `src/features/payment/cancelUnpaidOrder.ts`; chỉ dùng cho đơn chưa thu tiền. Đơn đã thu cần luồng hoàn tiền riêng.
- Hoàn tiền chưa triển khai theo yêu cầu tạm dừng của người dùng; không có API/nút hoàn tiền mới trong mốc này.

Xác minh gần nhất: backend compileJava + 40 tests Payment/Order PASS với mock; FE production build PASS; helper hủy đơn chưa trả tiền có 4 kiểm tra mock PASS. Kiểm tra TypeScript toàn FE còn bốn lỗi đã có tại AdminStaffPage (successInfo/setCopied), không sửa trong phạm vi Payment. Chưa tự thực hiện hủy/thanh toán thật qua sandbox hoặc kiểm thử khóa đồng thời PostgreSQL.

## Payment → Participant — 2026-10-23

Schedule & participant đã READY_TO_MAP. Payment map Transaction.participant → EventParticipant bằng
@ManyToOne LAZY, không cascade, FK transactions.participant_id giữ nguyên.
Không sửa entity/repository/service/API/migration của Member 5.

Tạo thanh toán tour qua POST /api/payments/zalopay/ với JWT chủ vé:

```json
{"participantId":7}
```

Không gửi orderId cùng participantId. amount/appUser nếu gửi vẫn không dùng làm giá/identity;
backend lấy registeredPrice và owner từ vé. Request chỉ nhận ID, không nhận UUID xác nhận từ client.
Khóa theo thứ tự schedule → participant → transaction. Tạo thanh toán yêu cầu vé PENDING,
lịch OPEN/FULL/CLOSED, thời hạn giữ chỗ/cutoff còn ít nhất 300 giây (giới hạn minimum của ZaloPay).
expire_duration_seconds tối đa 900, không vượt thời hạn còn lại lúc tạo. Nếu còn dưới 5 phút
thì trả 409, không tạo giao dịch/link vượt hạn giữ chỗ. Không cho có PAYMENT PENDING/SUCCEEDED khác trên vé.

Khi query xác nhận đúng số tiền, commit Transaction SUCCEEDED trước rồi gọi
ParticipantPaymentService.confirm(participantId, registrationReference đọc từ vé đã khóa, transactionId)
bằng service identity SCHEDULE_PAYMENT; khôi phục SecurityContext của người dùng sau lời gọi, không cấp quyền này cho JWT khách.
Không gọi API xác nhận thủ công và không tự sửa trạng thái vé/sức chứa.

Response query có participantConfirmation:
- CONFIRMED: vé đã được Member 5 xác nhận.
- REVIEW_REQUIRED: tiền đã thành công nhưng sai lần đăng ký, muộn hoặc vé không còn hợp lệ. Không báo vé đã xác nhận, không giữ chỗ lại.
Trường này là kết quả trả về từ contract Member 5, không lưu cột riêng trong transactions. Nếu xác nhận bị lỗi, giữ SUCCEEDED và worker thử lại bằng cách đọc confirmation_reference trên vé và schedule_payment_reviews; không thu tiền lần hai.

Payment không thêm bảng/cột/migration. Đọc registrationReference từ vé dưới khóa lịch → vé
để gọi contract Member 5. Chỉ áp dụng khi Member 5 vẫn chặn tái đăng ký vé đã có giao dịch;
contract Member 5 kiểm tra thêm thời điểm giao dịch so với lần đăng ký. Nếu mở tái đăng ký
sau này phải thiết kế lại tham chiếu thanh toán trước khi sử dụng luồng đó.
Hoàn tiền/consumer schedule_payment_reviews chưa triển khai.

API bổ sung GET /api/payments/zalopay/participants/{participantId}/payment: kiểm tra chủ vé,
ưu tiên giao dịch pending, trả data=null nếu chưa có. Query bằng transactionId, cancel và retry
cũng kiểm tra owner vé. Hủy giao dịch không hủy vé/giải phóng chỗ; lifecycle vé vẫn thuộc Member 5.
FE tour chưa được nối trong mốc backend này; trang kết quả FE hiện chủ yếu hiển thị chi tiết gear.

### Giới hạn database sau merge

Tài liệu Member 5 ghi migration V5 cho expires_at/registration_reference và các bảng audit/review,
nhưng source nhánh hiện tại chỉ có V1/V3/V4/V2026100901; không thấy file V5.
Cần Member 5 đưa migration gốc lên repository hoặc xác nhận DB thử đã áp dụng migration đó trước khi chạy luồng tour.
Payment không tạo thay migration thuộc Schedule. Chưa chạy migration Payment trên database chung.

Xác minh mốc Participant: compileJava + 52 tests Payment/Order PASS (mock), gồm giá vé server/participantId, quyền owner, hạn vé, thứ tự khóa, identity nội bộ và khôi phục context, CONFIRMED/REVIEW_REQUIRED, lỗi xác nhận giữ SUCCEEDED và worker retry. Chưa chạy DB/provider thật.

Mốc bỏ cột xác nhận: compileJava + 52 tests Payment/Order PASS (mock). Xác nhận lặp dùng contract idempotent của Member 5; lỗi xác nhận vẫn giữ SUCCEEDED. Query worker mới đọc confirmation_reference và schedule_payment_reviews chưa kiểm chứng trên PostgreSQL thật. Migration Payment chưa chạy lên database chung.

Return URL qua env: compileJava + 53 tests Payment/Order PASS (mock), gồm bỏ qua redirectUrl client và gửi/trả URL cấu hình kèm transactionId. Chưa thử redirect ZaloPay thực tế.
