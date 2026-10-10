-- Migration V4: Khởi tạo tài khoản ADMIN mặc định cho hệ thống nếu chưa tồn tại
-- Mật khẩu mặc định: Admin@123 (đã được hash bằng BCrypt)
INSERT INTO users (role_id, full_name, email, phone, password, status)
SELECT r.role_id, 'Hệ thống Quản trị viên', 'admin@treektour.com', '0901234567',
       '$2a$10$bdR6b.gKXe5twChnidwdzO0hvbhIrFhMd1OoBmdb4ULRQ6UYyVOrO', 'ACTIVE'
FROM roles r
WHERE r.role_name = 'ADMIN'
ON CONFLICT (email) DO NOTHING;
