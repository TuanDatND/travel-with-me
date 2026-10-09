# Module integration status

> File này là nguồn trạng thái chung để map entity giữa các thành viên. Cập nhật ngay sau mỗi mốc code quan trọng.

| Module | Owner | Tables / entities | Public IDs / quan hệ cần dùng | Contract đã chốt | Status | Last update | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Account | Member 1 | `roles`, `users` | `users.user_id`, `users.role_id` | Chưa chốt | NOT_STARTED | — | Module khác tạm dùng `userId: Long`. |
| Branch & staff | Member 1 | `branches`, `staff` | `branches.branch_id`, `staff.user_id`, `staff.branch_id` | Chưa chốt | NOT_STARTED | — | Module khác tạm dùng `branchId: Long`. |
| Product | Member 2 | `products` | `products.product_id` | Chưa chốt | NOT_STARTED | — | Order tạm dùng `productId: Long`. |
| Inventory | Member 2 | `inventory` | `inventory.branch_id`, `inventory.product_id` | Chưa chốt | NOT_STARTED | — | Order sẽ gọi service tồn kho, không tự tạo entity inventory. |
| Order | Member 3 | `orders`, `order_items` | `orders.user_id`, `orders.branch_id`, `order_items.product_id` | Entity, DTO, repository, CRUD API đã tạo; enum `OrderStatus`, `OrderItemType`, `OrderItemStatus` | IN_PROGRESS | 2026-10-09 | IDs ngoại hiện là `Long`; chưa map JPA sang các module khác. |
| Tour & guide | Member 4 | `tour_events`, `event_details`, `event_images`, `tour_guides` | `event_id`, `guide_id`, `user_id` | Chưa chốt | NOT_STARTED | — | — |
| Schedule & participant | Member 5 | `event_schedules`, `event_schedule_history`, `event_participants` | `schedule_id`, `event_id`, `branch_id`, `guide_id`, `user_id` | Chưa chốt | NOT_STARTED | — | — |
| Payment & report | Member 6 | `transactions` | `transaction_id`, `order_id`, `participant_id` | Chưa chốt | NOT_STARTED | — | Không map trước khi Order/Participant sẵn sàng. |

## Change log

| Date | Module | Author | Files / migration | What changed | Mapping impact |
| --- | --- | --- | --- | --- | --- |
| 2026-10-09 | Order | Member 3 | `order/entity`, `order/dto`, `order/repository` | Khởi tạo entity `Order`, `OrderItem`, DTO và repository | Chờ Account, Branch, Product chuyển sang `READY_TO_MAP` trước khi dùng `@ManyToOne`. |
| 2026-10-09 | Order | Member 3 | `order/service`, `order/controller` | Thêm CRUD `POST/GET/PUT/DELETE /api/orders` và validation ngày thuê | Chưa gọi Product/Inventory; giá và tồn kho vẫn là dữ liệu request tạm thời. |

## Quy tắc map entity

Chỉ map quan hệ JPA khi module đích có trạng thái `READY_TO_MAP`. Khi map, cập nhật change log với các thông tin: entity nguồn, entity đích, cột FK, loại quan hệ, `fetch` strategy và migration (nếu có).
