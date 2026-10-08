Phân công nhóm phát triển web
Hệ thống mua và thuê thiết bị kết hợp tour trekking và sự kiện cắm trại
Nhóm 6 thành viên chia theo chức năng. Mỗi người phụ trách cả API và giao diện của phần mình, đồng thời viết validation, kiểm thử luồng chính và tài liệu API. Số thành viên dưới đây là vị trí phân công, chưa phải tên được chốt.
Phân công cho 6 thành viên
STT
Phần phụ trách
Chức năng
Bảng chính
1
Tài khoản và chi nhánh
Đăng ký, đăng nhập, hồ sơ cá nhân, phân quyền; quản lý chi nhánh và nhân viên; kiểm tra quyền theo chi nhánh.
users, roles, branches, staff
2
Thiết bị và tồn kho
Danh sách, tìm kiếm, lọc và chi tiết sản phẩm; quản lý sản phẩm; nhập và cập nhật tồn theo chi nhánh; hiển thị lượng có thể thuê.
products, inventory
3
Mua và thuê thiết bị
Giỏ hàng; chọn mua hoặc thuê theo ngày; tạo và xem đơn; xác nhận, hủy; giao thiết bị, nhận trả và cập nhật tồn kho.
orders, order_items
4
Tour và hướng dẫn viên
Quản lý tour và sự kiện cắm trại; nội dung, yêu cầu, điểm tập trung, ảnh; danh sách và chi tiết tour cho khách; quản lý hướng dẫn viên.
tour_events, event_details, event_images, tour_guides
5
Lịch và đăng ký tham gia
Tạo lịch, phân công hướng dẫn viên, giới hạn chỗ; đăng ký, hủy, danh sách người tham gia, check-in; đổi lịch tổ chức và lưu lịch sử.
event_schedules, event_schedule_history, event_participants
6
Thanh toán và báo cáo
Thanh toán đơn và đăng ký tour; lịch sử giao dịch; hoàn tiền; thống kê doanh thu theo thời gian và chi nhánh, đơn hàng, người tham gia.
transactions và dữ liệu đọc từ các phần khác

Phối hợp và phạm vi triển khai
Quy ước phối hợp
Các phần
Trách nhiệm và đầu nối
2 và 3
Người 2 quản lý sản phẩm và tồn kho. Người 3 tăng giảm tồn khi xử lý đơn, thông qua service thống nhất. Tránh cập nhật tồn ở nhiều nơi.
3 và 6
Thanh toán thành công gọi nghiệp vụ cập nhật đơn của phần 3. Thống nhất trạng thái đơn và xử lý thông báo thanh toán lặp.
5 và 6
Thanh toán tour gắn với participant_id. Chốt thời điểm giữ chỗ, xác nhận đăng ký và giải phóng chỗ khi hủy hoặc hết hạn.
1 và tất cả
Dùng chung thông tin đăng nhập, role và chi nhánh. Backend kiểm tra quyền truy cập, không chỉ ẩn nút trên giao diện.

đã xuất trước đó.
Phạm vi bản đầu
Thuê theo ngày và số lượng; nhận và trả thiết bị tại chi nhánh.
Mỗi lượt đăng ký tour dành cho một người; thanh toán sandbox hoặc xác nhận thủ công.
Nếu đặt thuê trước, kiểm tra tổng số lượng đã được đặt trong các khoảng ngày trùng nhau. quantity_available hiện tại không đủ để quyết định khả năng đáp ứng đơn thuê tương lai.
Luồng cần hoàn thành sớm
Thiết bị: chọn sản phẩm → đặt đơn → thanh toán → nhận thiết bị → trả thiết bị đối với đơn thuê.
Tour: chọn tour → chọn lịch → đăng ký → thanh toán → check-in.
Điều kiện hoàn thành của mỗi phần
API và giao diện hoạt động cùng nhau; có kiểm tra dữ liệu và quyền truy cập; kiểm thử được luồng chính cùng các lỗi quan trọng; tài liệu API và dữ liệu mẫu đủ để các thành viên khác ghép hệ thống.

