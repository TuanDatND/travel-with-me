INSERT INTO roles (role_name, description) VALUES ('ADMIN', 'Quản trị viên toàn hệ thống') ON CONFLICT (role_name) DO NOTHING;
INSERT INTO roles (role_name, description) VALUES ('STAFF', 'Nhân viên quản lý và vận hành theo chi nhánh') ON CONFLICT (role_name) DO NOTHING;
INSERT INTO roles (role_name, description) VALUES ('GUIDE', 'Hướng dẫn viên tour trekking') ON CONFLICT (role_name) DO NOTHING;
INSERT INTO roles (role_name, description) VALUES ('CUSTOMER', 'Khách hàng sử dụng dịch vụ') ON CONFLICT (role_name) DO NOTHING;
