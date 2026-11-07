# Bổ sung trường mới vào TKB SV

**Hiện chỉ hỗ trợ TNUT.** Cấu trúc mới chuẩn bị cho nhiều trường, chưa tích
hợp API/cổng đăng nhập của trường thứ hai.

## Tổ chức nguồn

- `schools/SchoolProvider.kt`: interface chung (repository, session, token).
- `schools/SchoolRegistry.kt`: danh sách provider.
- `schools/tnut/`: API, model, HTTP, repository và adapter TNUT.
- `data/`: Room/SQLite, ghi chú, cấu hình (chưa đổi schema).
- `ui/`: gọi `SchoolRepository` và `SchoolSession`, không gọi Retrofit TNUT.
- `rust-core/src/portal.rs`: parser **chỉ** dành cho TNUT.

## Khi triển khai trường thứ hai

1. Tạo `schools/<mã-trường>/` implement `SchoolProvider`, `SchoolSession`,
   `SchoolRepository`, chuyển dữ liệu trường đó thành entity lịch chung.
2. Đăng ký adapter ở `SchoolRegistry`. Không đưa token TNUT sang host khác.
3. **Trước khi bật chọn trường**, cách ly cache, ghi chú, token và avatar
   theo cả trường và tài khoản. Schema Room hiện chưa có school/account key.
   Bật đổi trường ngay sẽ làm trộn dữ liệu.
4. Nâng cấp bằng migration không phá hủy `tkb.db` từ các bản v1–v4.
   Giữ `applicationId=com.tkb.sv`, chữ ký APK và ghi chú cũ.
5. Thêm test API, lịch/thi, offline, auth refresh, R8 và Rust JNI của trường mới.

`AuthStore.schoolId` mặc định `tnut`, bảo đảm các bản TNUT cũ tiếp tục
đăng nhập, xem lịch và ghi chú như hiện tại. Chưa có nút đổi trường.
