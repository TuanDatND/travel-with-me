# Chuẩn cấu trúc backend TreekTour

Cập nhật: 2026-10-24. Đây là quy ước thống nhất của nhóm cho project này, không phải khẳng định mọi dự án Spring đều phải có cùng cấu trúc. Nếu rubric của giảng viên có yêu cầu khác, nhóm phải cập nhật quy ước trước khi áp dụng.

## 1. Cấu trúc package bắt buộc

Gốc Java: `com.coc.sba_treektour`. Mỗi nghiệp vụ có một module dưới `modules`; bên trong chia theo trách nhiệm. Không đặt toàn bộ controller/service/entity vào chung một package, không đặt implementation trực tiếp ở package gốc module.

```text
src/main/java/com/coc/sba_treektour/
├── SbaTreekTourApplication.java
├── common/
│   ├── config/                  # Security, cấu hình dùng chung
│   ├── exception/               # Xử lý lỗi thực sự dùng chung
│   ├── response/                # ApiResponse
│   └── util/                    # Tiện ích không chứa nghiệp vụ module
└── modules/
    ├── account/
    ├── branch/
    ├── product/
    ├── inventory/
    ├── order/
    │   ├── controller/
    │   │   └── OrderController.java
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── CreateOrderRequest.java
    │   │   │   └── CreateOrderItemRequest.java
    │   │   └── response/
    │   │       ├── OrderResponse.java
    │   │       └── OrderItemResponse.java
    │   ├── entity/
    │   │   ├── Order.java
    │   │   └── OrderItem.java
    │   ├── enums/
    │   │   ├── OrderStatus.java
    │   │   ├── OrderItemStatus.java
    │   │   └── OrderItemType.java
    │   ├── exception/
    │   │   ├── InvalidOrderException.java
    │   │   ├── OrderNotFoundException.java
    │   │   └── OrderExceptionHandler.java
    │   ├── repository/
    │   │   ├── OrderRepository.java
    │   │   └── OrderItemRepository.java
    │   └── service/
    │       ├── OrderService.java
    │       └── impl/
    │           └── OrderServiceImpl.java
    ├── payment/
    ├── event/
    ├── tour/
    └── schedule/

src/test/java/com/coc/sba_treektour/modules/order/
└── service/impl/OrderServiceImplTests.java

src/main/resources/db/migration/
└── V<version>__<description>.sql
```

Các module khác dùng cùng mẫu. Chỉ thêm `config`, `mapper`, `validator`, `projection`, `scheduler` khi thực sự có class tương ứng; không tạo hàng loạt class rỗng chỉ để đủ cây thư mục.

## 2. Service interface và implementation

Mọi service nghiệp vụ công khai phải có interface ở `service` và implementation ở `service.impl`:

```java
// modules/order/service/OrderService.java
package com.coc.sba_treektour.modules.order.service;

import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;

public interface OrderService {
    OrderResponse findById(Long id);
}
```

```java
// Khung minh họa; giữ phần nghiệp vụ đang có trong project khi refactor.
package com.coc.sba_treektour.modules.order.service.impl;

import com.coc.sba_treektour.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    // private final Dependencies...;
    // Triển khai đầy đủ các method của OrderService với @Override.
}
```

Đoạn implementation trên chỉ minh họa vị trí/annotation, không phải class hoàn chỉnh có thể copy để compile. `@Transactional` đặt trên method nghiệp vụ trong implementation; đọc dùng `readOnly = true`, ghi giữ transaction để thay đổi trạng thái và tồn kho cùng thành công hoặc rollback.

Controller và module gọi phải inject interface:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
}
```

Không inject `OrderServiceImpl`, không `new OrderServiceImpl(...)` trong controller/service. Khởi tạo implementation trực tiếp trong unit test với mock dependency là được phép. Không để hai implementation cùng được Spring đăng ký cho một interface nếu chưa phân biệt bằng qualifier.

Không cần tạo interface/impl cho entity, DTO, enum, exception hoặc mapper thuần chỉ để đáp ứng hình thức. Repository JPA cũng không phải service implementation.

## 3. `implements`, `extends` và import

Viết:

```java
import com.coc.sba_treektour.modules.payment.service.OrderPaymentQueryService;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderPaymentQueryService orderPaymentQueryService;
}
```

Không viết:

```java
// Sai vai trò: implementation không phải interface để implements.
class Example implements payment.PaymentServiceImpl { }

// Tránh ghi nguyên package trong khai báo; dùng import và tên class ngắn.
private final com.coc.sba_treektour.modules.payment.service.OrderPaymentQueryService payment;

// Tránh phụ thuộc implementation của module khác.
private final PaymentServiceImpl paymentService;
```

`package` là namespace Java, không phải đường dẫn file để gọi implementation. Khai báo package phải khớp vị trí thư mục. Quy ước nhóm: import tường minh, không wildcard; dùng `BigDecimal`, `LocalDate`, `@Query` qua import thay vì tên đầy đủ trong thân class. Ngoại lệ khi hai kiểu cùng simple name xung đột: có thể viết fully qualified name cho một kiểu và giải thích nếu cần.

Repository JPA:

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
}
```

Đây là **interface extends interface**; Spring Data tạo proxy implementation. Không tự tạo `OrderRepositoryImpl` chỉ để gọi `save/findById`. Query đặt ở repository bằng derived method hoặc `@Query`; không viết SQL trong controller/service. Chỉ dùng custom implementation khi Spring Data query không đáp ứng được, không phải vì mọi lớp phải có hậu tố Impl.

## 4. Trách nhiệm từng package

| Package | Được làm | Không làm |
| --- | --- | --- |
| controller | Nhận request, `@Valid`, xác thực quyền endpoint, gọi service, trả DTO | Tính giá, sửa tồn kho, truy vấn DB trực tiếp |
| dto.request | Dữ liệu client gửi và validation | Entity JPA; tự tin giá/quyền/trạng thái client khai |
| dto.response | Dữ liệu trả ra; chỉ field cần cho UI | Password, token nội bộ, lazy entity graph |
| entity | Mapping bảng/quan hệ và helper giữ quan hệ nhất quán | HTTP, gọi provider, chứa repository |
| enums | Các enum nghiệp vụ, tên giá trị khớp schema | Đổi enum khi chưa thống nhất constraint/migration |
| repository | Truy vấn, projection và lock database | Gọi API thanh toán hoặc quyết định luồng HTTP |
| service | Interface công khai của nghiệp vụ | Code truy vấn SQL hoặc implementation |
| service.impl | Validation nghiệp vụ, điều phối, transaction, gọi repository/service interface | Inject implementation của module khác; tự trả Entity qua HTTP |
| exception | Exception nghiệp vụ và handler của module | Nuốt lỗi rồi giả lập thành công |

`common` chỉ dành cho thành phần nhiều module thật sự dùng. Không chuyển Order/Payment/Tour business logic vào common để né việc thiết kế đầu nối.

## 5. Quy ước tên và Lombok

- Package chữ thường, chính tả: `request`, `response`, `repository`, `service`, `impl`, `exception`, `enums`. Không dùng `requset`, `respone`, `sevices`.
- Class/interface PascalCase: `OrderService`, `OrderServiceImpl`, `OrderResponse`. Biến camelCase và rõ vai trò: `productRepository`, `inventoryService`, `orderPaymentQueryService`.
- Mỗi public top-level type một file trùng tên. DTO request và response tách riêng; record được dùng vì project hỗ trợ Java 25.
- Controller/service có dependency final dùng `@RequiredArgsConstructor`; không field injection `@Autowired`.
- Entity dùng `@Getter`, `@NoArgsConstructor(access = PROTECTED)`; chỉ mở setter cần thiết. Giữ constructor/helper nghiệp vụ có ý nghĩa. Không dùng `@Data` tự sinh equals/hashCode/toString trên quan hệ JPA vì có thể kéo lazy loading/vòng lặp.
- `@Builder` chỉ dùng khi giúp khởi tạo rõ hơn; không bắt buộc mọi entity/DTO phải có. Record không cần Lombok getter/constructor.
- Method interface đã triển khai phải có `@Override`. Imports tách khỏi package bằng một dòng trống. Không giữ code comment-out đã bỏ hoặc gom nhiều trách nhiệm vào một method cho tiện.

## 6. Ghép module

Luồng chuẩn của Order hiện tại:

```text
OrderController → OrderService → OrderServiceImpl
                                    ├── OrderRepository
                                    ├── InventoryService (đầu nối hiện có)
                                    └── OrderPaymentQueryService (interface Payment)
                                            → OrderPaymentQueryServiceImpl
                                            → TransactionRepository
```

Order quyết định cho duyệt/hủy; Payment cung cấp snapshot giao dịch đã lưu. Không tạo OrderPaymentRepository truy vấn trùng transactions. Không gọi provider trong lần mở danh sách đơn. Không tạo entity của người khác trong module mình để ứng phó thiếu code.

Khi module đích chưa có service interface, ghi rõ đầu nối còn chờ trong MODULE_INTEGRATION_STATUS.md; owner chịu trách nhiệm tách interface. Hiện Order còn dùng các đầu nối Product/Inventory đang là concrete service hoặc repository đọc/lock; đây là điểm cần owner phối hợp tiếp, không phải mẫu khuyến nghị để mở rộng phụ thuộc liên module. Không tự sửa nghiệp vụ của thành viên khác; đổi import bắt buộc do đổi tên package của module mình thì được cập nhật cơ học và phải ghi lại.

## 7. Schema và API khi refactor cấu trúc

Đổi package/import không được tự thay endpoint, tên field JSON, PK/FK, enum value hoặc luồng thanh toán/giao/trả. Entity đổi field phải có migration; không sửa migration đã được chạy trên DB của nhóm. Không tắt validate hoặc chạy repair để che lỗi thiếu migration.

Đối với endpoint chưa hỗ trợ nghiệp vụ (ví dụ cập nhật đơn sau thanh toán), ghi rõ giới hạn thay vì tạo implementation giả hoặc trả thành công mà không làm gì. Tách cấu trúc và thay nghiệp vụ thành các commit khác nhau để dễ review.

## 8. Các điểm còn cần owner module khác rà soát

Khảo sát source hiện có ngày 2026-10-24, không phải xác nhận các module đã được sửa:

| Module | Điểm cần đối chiếu chuẩn |
| --- | --- |
| Account / Branch | Đã có service interface + service.impl; owner rà DTO request/response, enums và import |
| Order | Đã chuẩn hóa DTO, enums, exception, interface/impl và imports ở mốc này |
| Payment | OrderPaymentQueryService đã có impl riêng; TransactionsServiceImpl còn ở service, cần owner chuyển vào service.impl và cập nhật test/import |
| Product / Inventory | ProductService, InventoryService, RentalBookingService đang là concrete service; owner cần tách interface/impl |
| Event / Tour | EventService, GuideService, GuideRegistrationService còn là concrete service; owner cần tách interface/impl |
| Schedule | Có service interface/impl; owner rà các helper, DTO, enums và dependency liên module theo cùng checklist |

Không suy ra code đúng chỉ vì đủ folder; phải kiểm tra trách nhiệm và dependency bên trong.

## 9. Checklist trước khi nộp/merge

- [ ] Package khớp đường dẫn, chính tả đúng, mỗi type ở đúng package.
- [ ] Service nghiệp vụ có interface và class Impl ở service.impl.
- [ ] Controller/module khác inject interface, không inject hoặc implements một Impl.
- [ ] Repository extends JpaRepository/Repository phù hợp; query nằm ở repository sở hữu dữ liệu.
- [ ] Request/response/enums/exception tách đúng; không trả entity trực tiếp.
- [ ] Lombok dùng đúng, không lộ setter/equals/toString cho quan hệ JPA ngoài ý muốn.
- [ ] Không wildcard import/tên package dài trong code nếu có thể import bình thường.
- [ ] Refactor không đổi contract, schema hoặc nghiệp vụ ngoài phạm vi.
- [ ] `gradlew.bat compileJava compileTestJava` qua; chạy test của module bị ảnh hưởng.
- [ ] Nếu đổi FE hoặc API contract: frontend `npm run lint` và `npm run build` qua.
- [ ] Cập nhật MODULE_INTEGRATION_STATUS.md: file đã đổi, contract, kết quả kiểm tra và hạn chế chưa test.

Prompt cho AI:

> Đọc doc/PROJECT_STRUCTURE.md, doc/AI_COLLABORATION_RULES.md và doc/MODULE_INTEGRATION_STATUS.md trước khi sửa. Tôi phụ trách module X. Chuẩn hóa theo service interface + service.impl, dto.request/response, entity/enums/repository/exception; import tường minh, dùng Lombok phù hợp, không tham chiếu implementation qua tên package. Giữ contract/nghiệp vụ; chỉ sửa module X và import phụ thuộc bắt buộc, chạy compile/test và cập nhật trạng thái.
