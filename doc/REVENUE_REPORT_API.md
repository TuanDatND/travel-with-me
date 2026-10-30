# Revenue report backend

Cập nhật 2026-10-30. Module Payment; frontend report chưa nối API trong mốc này.
Cấu trúc: RevenueReportController → RevenueReportService → service.impl → RevenueReportRepository.
Repository dùng NamedParameterJdbcTemplate cho SQL tổng hợp/PostgreSQL, không đặt SQL ở service/controller.

## Thời điểm ghi nhận thành công

Migration `V2026103002__transaction_succeeded_at.sql` thêm `transactions.succeeded_at TIMESTAMPTZ NULL`
và partial index `(succeeded_at, transaction_id) WHERE status='SUCCEEDED'`. PK/FK/enum giữ nguyên.
Migration không backfill giao dịch cũ vì không biết ngày thành công thật.

- PAYMENT: thời điểm backend lần đầu xác minh thành công từ query/worker ZaloPay, sau khi kiểm tra số tiền.
  Đây là giờ hệ thống ghi nhận thành công, không phải thời gian trả tiền chính xác từ provider; kết quả query
  hiện chưa lưu/đọc thời gian thanh toán của ZaloPay. Người dùng đóng tab có thể khiến xác minh đến muộn.
- REFUND/MANUAL: lúc ADMIN xác nhận đã chuyển tiền lại bên ngoài ứng dụng.
- Ghi cùng transaction DB với trạng thái SUCCEEDED; xử lý lặp không thay đổi succeeded_at.
- Không đổi created_at; history response bổ sung succeededAt, các field cũ giữ nguyên.
- Giao dịch SUCCEEDED cũ chưa có succeeded_at không bị âm thầm tính theo created_at. Overview trả
  số lượng và tiền thu/hoàn chưa có thời gian riêng, ngoài số liệu trong kỳ.
- Không sửa/lấp timestamp cũ chỉ vì query hoặc xác nhận lặp. Muốn đối soát/backfill cần nguồn thời gian
  xác thực và quy trình riêng.

Phải áp migration mới trước khi chạy entity mapping mới. Các migration trước đó, gồm
V2026103001 refund_reason, vẫn cần được áp đúng trình tự. Không dùng ddl-auto=update để né migration.

## Quyền và tham số

Tất cả endpoint dưới `/api/reports/revenue` yêu cầu JWT ADMIN và account còn ACTIVE, kiểm tra tại service.
Đọc bằng transaction read-only REPEATABLE_READ để các tổng/biểu đồ/bảng trong cùng response nhất quán.
Không gọi ZaloPay trong report. Không sửa Order/Schedule, không tự đổi nghiệp vụ hoặc dữ liệu của module khác.

Tham số chung:

| Tham số | Giá trị |
| --- | --- |
| from, to | Bắt buộc YYYY-MM-DD, hai ngày được bao gồm, tối đa 366 ngày |
| period | DAY mặc định hoặc MONTH |
| branchId | ID chi nhánh dương, tùy chọn |
| sector | EQUIPMENT hoặc TOUR, tùy chọn |
| paymentMethod | Ví dụ ZALOPAY hoặc MANUAL, tùy chọn, so sánh chính xác |
| tourId, scheduleId | ID dương, tùy chọn để giới hạn tiền thuộc tour/lịch |
| productId | Chỉ dùng endpoint products; không phân bổ tiền thu/hoàn theo sản phẩm |

Múi giờ cố định `Asia/Ho_Chi_Minh`: from 00:00 bao gồm, ngày sau to 00:00 không bao gồm.
Không trộn productId với tourId/scheduleId/sector TOUR hoặc tourId/scheduleId với sector EQUIPMENT.
Endpoint products chỉ nhận dates/period/branchId/productId (sector EQUIPMENT có thể truyền);
không nhận lọc paymentMethod vì metric sản phẩm không phải phân bổ tiền theo phương thức.

## 1. Tổng quan

`GET /api/reports/revenue?from=2026-10-01&to=2026-10-31&period=DAY&branchId=1`

ApiResponse.data:
- from/to/timezone/timeBasis=`BACKEND_VERIFIED_SUCCESS_AT`.
- totals: receivedAmount, refundedAmount, netAmount, paymentCount, refundCount.
- pendingRefundAmount/pendingRefundCount: backlog hoàn đang PENDING **hiện tại**, áp bộ lọc
  chi nhánh/sector/tour/lịch/method, không áp thời gian thành công vì pending chưa có thời gian đó.
- undatedSucceededCount/undatedReceivedAmount/undatedRefundedAmount: toàn bộ SUCCEEDED chưa có
  ngày thành công, áp cùng bộ lọc nhưng không áp from/to. Không cộng các giá trị này vào totals.
- series: date, receivedAmount, refundedAmount, netAmount; overview điền 0 cho bucket không có giao dịch.
- branches: id/name chi nhánh, receivedAmount/refundedAmount/netAmount/paymentCount/refundCount.
- sectors: cùng cấu trúc, id=1/name=EQUIPMENT, id=2/name=TOUR. Không tự tách tiền mua/thuê.

Chỉ SUM giao dịch SUCCEEDED; PAYMENT là thu, REFUND là hoàn. netAmount không ép về 0.
Thu tháng 10 và hoàn tháng 11 nằm ở hai kỳ tương ứng. Các count là số giao dịch, không phải
số đơn/khách; không gọi paymentCount là số đơn duy nhất. Nguồn chỉ gồm tiền đã ghi transactions;
xác nhận vé thủ công không tạo transaction sẽ không xuất hiện. Đây là báo cáo tiền thu/hoàn,
không phải lợi nhuận, doanh thu kế toán hoàn thành dịch vụ, hoặc dòng tiền đối soát ngân hàng.

Donut dùng receivedAmount của sectors; không dùng netAmount vì có thể âm.

## 2. Sản phẩm

`GET /api/reports/revenue/products?from=2026-10-01&to=2026-10-31&period=MONTH&productId=1`

- metric=`ITEM_VALUE_IN_FULLY_PAID_ORDERS_BEFORE_REFUNDS`.
- timeBasis=`FIRST_FULL_GROSS_PAYMENT_AT`.
- products: productId/productName, saleQuantity, rentalQuantity, rentalDays, paidOrderCount,
  saleValue, rentalValue, totalValue.
- series: date/productId/productName, saleValue/rentalValue/totalValue. Có dữ liệu để vẽ nhiều
  đường theo sản phẩm hoặc cột mua/thuê; không giới hạn chỉ Top N. Bucket không có dữ liệu bị bỏ qua.

Đơn được tính một lần tại thời điểm tổng PAYMENT SUCCEEDED lũy kế lần đầu đủ orders.total_amount.
Đơn có PAYMENT SUCCEEDED không biết timestamp bị loại để không suy đoán ngày đủ tiền.
Đơn tổng 0 không thuộc metric này. Số tiền sản phẩm dùng snapshot order_items.unit_price,
quantity, thuê bao gồm cả ngày nhận và trả; rentalDays là tổng số lượng × số ngày.
Không dùng giá hiện tại của products; tên sản phẩm là tên hiện tại và có thể thay đổi.

Các item vẫn thể hiện giá trị đơn gốc dù đơn được hoàn sau đó. Không trừ hoàn chung vào từng
sản phẩm, không tự chia tỷ lệ hoặc đếm tiền transaction lặp theo từng item.
paidOrderCount mỗi sản phẩm là số đơn khác nhau có sản phẩm đó, không cộng ngang để thành tổng số đơn.
Không gọi chỉ số này là tiền thu ròng/doanh thu ròng từng sản phẩm. Muốn làm vậy cần dữ liệu phân bổ
thu/hoàn tới từng order_item từ nghiệp vụ riêng.

## 3. Tour và từng đợt tổ chức

`GET /api/reports/revenue/tours?from=2026-10-01&to=2026-11-30&tourId=1`

- tours: tourId/tourName, paidTicketCount, receivedAmount/refundedAmount/netAmount.
- series: date/tourId/tourName, receivedAmount/refundedAmount/netAmount, dành cho biểu đồ theo tour.
- schedules: scheduleId/tourId/tourName/startDatetime/branchId/branchName, paidTicketCount,
  receivedAmount/refundedAmount/netAmount để so sánh các lịch khởi hành.

paidTicketCount đếm DISTINCT participant_id có PAYMENT thành công trong kỳ, không đếm các lần retry.
Kỳ chỉ có REFUND có thể có paidTicketCount=0 và netAmount âm. Tên tour/chi nhánh là dữ liệu hiện tại.
Có thể lọc scheduleId và branchId cùng tourId. Tour/schedule series không tự sinh bucket 0.

## 4. Chi tiết và CSV

`GET /api/reports/revenue/transactions?from=2026-10-01&to=2026-10-31&page=0&size=20`

Trả content/page/size/totalElements/totalPages. size=1..100, page>=0.
Mỗi dòng: transactionId/orderId/participantId/branchId/branchName/tourId/tourName,
transactionType/paymentMethod/amount/createdAt/succeededAt.
Sort succeededAt DESC, transactionId DESC; chỉ giao dịch thành công có ngày trong kỳ.

`GET /api/reports/revenue/export?from=2026-10-01&to=2026-10-31`

Cùng filter tiền thu/hoàn; text/csv UTF-8 BOM, attachment transactions.csv. Tối đa 10.000 dòng;
vượt giới hạn trả 400 và yêu cầu thu hẹp filter, không xuất thiếu một phần mà không báo.
Tên có dấu phẩy/dấu ngoặc kép được escape, chuỗi có thể tạo công thức spreadsheet được xử lý.
Các timestamp CSV là ISO offset; report bucket dùng ngày Việt Nam.

## Kiểm tra

Linux dùng `bash gradlew` thay `gradlew.bat`:

```bash
REPORT_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55438/report_check bash gradlew compileJava compileTestJava test --tests 'com.coc.sba_treektour.modules.payment.*' --tests 'com.coc.sba_treektour.modules.order.service.impl.*' --offline --console=plain
```

89 tests PASS, 0 failures/errors/skipped, gồm 7 test RevenueReportRepository trên PostgreSQL 18
local riêng, schema ngẫu nhiên được xóa sau test. Test không dùng .env/Supabase; fixture chạy V1 và
hai migration Payment mới. Kiểm tra thu/hoàn khác kỳ, ngày Việt Nam, giá item snapshot/thuê đủ ngày,
tránh nhân tiền qua JOIN, nhiều lần trả tiền, timestamp cũ NULL, filters, pagination, tour/schedule,
admin method security, HTTP validation, CSV và ghi timestamp chống lặp. FE hiện tại lint/build PASS;
chưa nối trang report FE và chưa thử ZaloPay live hoặc migration trên Supabase.

Repository tests yêu cầu REPORT_TEST_DB_URL local:55438 để tránh chạy nhầm database chung; nếu không
truyền biến thì các test DB sẽ bị skip, không được báo rằng đã kiểm chứng SQL chỉ từ test mock.

## Local fixture check — 2026-10-30

Bộ SQL và README ngoài repo: Desktop/TreekTour-AdminReport-LocalDB. Đã restore vào DB local mới, Flyway validate 11 migrations, Hibernate ddl-auto=validate, login admin demo và 5 API overview/products/tours/transactions/export trả HTTP 200. Phát hiện/sửa HV000151 bằng cách đặt @Valid trên tham số interface RevenueReportService, không khai báo lại constraint ở impl. Test method-validation proxy đã bổ sung; 89 tests PASS. Supabase chỉ đọc metadata, không copy dữ liệu hoặc chạy migration.

## Frontend integration — 2026-10-30

AdminReportsPage tại /admin/reports đã nối API thật (features/reports/reportApi.ts), dùng shared JWT.
Ba tab Tổng quan/Sản phẩm/Tour & khởi hành; bộ lọc có nút Áp dụng, tách filter theo contract từng endpoint.
Biểu đồ theo ngày/tháng, zero-fill, thu/hoàn/ròng, chi nhánh, donut tiền thu, so sánh sản phẩm/tour
và nhiều đường có chọn tối đa 6 đối tượng; bảng item/schedule, transaction phân trang 20 dòng.
CSV chỉ hiện cho báo cáo tiền (Overview/Tour), Tour luôn thêm sector=TOUR cho detail/export.
Sản phẩm hiển thị giá trị item trong đơn đủ tiền trước hoàn, không gắn nhãn thu ròng.
Hiển thị cảnh báo legacy missing timestamp và pending refund là backlog hiện tại ngoài kỳ.
Dữ liệu lỗi/loading/empty không thay bằng số mock; request cũ bị abort khi đổi filter/tab.
Lint/build và 6 FE tests PASS. Browser local admin xác nhận tổng tháng 10 khớp dữ liệu demo, các
tab Product/Tour có dữ liệu thật, tải CSV Tour 71 dòng và chỉ chứa participant targets.
