# Quy ước dùng AI và phối hợp code

Mục tiêu là để các module có thể làm song song nhưng vẫn ghép entity được, không ghi đè công việc của nhau.

## Quy tắc bắt buộc khi dùng AI

1. Chỉ yêu cầu AI sửa module mình phụ trách. Không tự ý tạo/sửa entity, migration, repository hoặc API thuộc module khác.
2. Trước khi code, đọc `doc/MODULE_INTEGRATION_STATUS.md` để biết entity nào đã sẵn sàng để map quan hệ.
3. Sau **mỗi mốc có ý nghĩa** (tạo entity, đổi cột/schema, thêm DTO, repository, API, migration, test), cập nhật ngay file trạng thái. Không chờ làm xong toàn bộ tính năng.
4. Một thay đổi database phải nêu rõ: tên bảng, PK, FK/ID tham chiếu, enum/trạng thái và migration liên quan.
5. Khi module khác chưa hoàn thành entity, chỉ lưu khóa tham chiếu dạng `Long` (ví dụ `productId`) trong module mình. Chỉ đổi sang `@ManyToOne` sau khi trạng thái entity đích là `READY_TO_MAP`.
6. Không đổi tên bảng/cột/enum đã đánh dấu `READY_TO_MAP` nếu chưa báo trong nhóm và cập nhật file trạng thái trước.
7. AI phải chạy `compileJava` sau khi sửa Java. Nếu không chạy được thì ghi rõ nguyên nhân trong mục ghi chú của module.

## Cách cập nhật trạng thái

Sửa duy nhất bảng trong `doc/MODULE_INTEGRATION_STATUS.md`.

- `NOT_STARTED`: chưa có code.
- `IN_PROGRESS`: đang làm, chưa nên module khác map quan hệ JPA.
- `READY_TO_MAP`: entity/table/ID contract đã ổn định; module khác được phép map FK hoặc `@ManyToOne`.
- `INTEGRATED`: đã ghép quan hệ và kiểm tra build.

Mỗi cập nhật cần có đủ:

```text
Ngày | Người phụ trách | file đã sửa | bảng/entity | ID/FK public | thay đổi contract | trạng thái
```

## Prompt mẫu cho AI

```text
Tôi phụ trách module <module>. Chỉ sửa file trong package <package> và migration do module này sở hữu.
Không tạo/sửa code của module khác. Sau khi hoàn thành một mốc, cập nhật đúng dòng module của
doc/MODULE_INTEGRATION_STATUS.md với entity, bảng, PK/FK hoặc ID public, DTO/API contract và trạng thái.
Chạy compileJava trước khi kết thúc.
```
