-- Thêm role BRANCH_ADMIN: quản lý chi nhánh, cao hơn STAFF nhưng thấp hơn ADMIN tổng.
INSERT INTO roles (role_name, description)
VALUES ('BRANCH_ADMIN', 'Quản trị viên chi nhánh, quản lý nhân viên và dữ liệu của chi nhánh mình')
ON CONFLICT (role_name) DO NOTHING;
