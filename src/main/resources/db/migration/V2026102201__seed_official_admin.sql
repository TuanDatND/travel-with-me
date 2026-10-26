-- Migration V2026102201: Khởi tạo / cập nhật tài khoản ADMIN chính thức admin@gmail.com / admin123
INSERT INTO users (role_id, full_name, email, phone, password, status)
SELECT r.role_id, 'Admin Administrator', 'admin@gmail.com', '0901234567',
       '$2a$10$R4SGt8WHOjSbzOV9zZLtYuo1IJdBZwZwVtRW6urxnXAQn10qVfUiK', 'ACTIVE'
FROM roles r
WHERE r.role_name = 'ADMIN'
ON CONFLICT (email) DO UPDATE
SET password = EXCLUDED.password,
    role_id = EXCLUDED.role_id,
    status = 'ACTIVE';
