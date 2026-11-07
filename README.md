# TKB SV

Ứng dụng thời khóa biểu mã nguồn mở cho sinh viên ĐH Kỹ thuật Công nghiệp Thái Nguyên (TNUT).

Lấy lịch trực tiếp từ [portal.tnut.edu.vn](https://portal.tnut.edu.vn) bằng tài khoản sinh viên của bạn — **không qua máy chủ trung gian nào**.

## Tính năng (v1)

- Đăng nhập bằng tài khoản portal TNUT
- Xem thời khóa biểu dạng tuần (chuyển tuần trước/sau, tự nhảy về tuần hiện tại)
- Mỗi buổi học hiện: tên môn, tiết + giờ, phòng, giảng viên, lớp, nhóm, số tín chỉ
- Lưu lịch vào Room (SQLite) trên máy: mất mạng vẫn xem được lịch đã tải
- Tải lại lịch từ portal bất cứ lúc nào

## Bảo mật

- Tài khoản + token chỉ lưu trên máy, mã hóa bằng EncryptedSharedPreferences. App không gửi tài khoản của bạn đi đâu ngoài `portal.tnut.edu.vn`.
- Dữ liệu thời khóa biểu lưu trong Room trên máy, không đồng bộ lên đâu.

## Dữ liệu thời khóa biểu và lịch thi

- Ứng dụng **không tạo sẵn lịch học hoặc lịch thi của người khác**. Khi đăng nhập, lịch thực tế được lấy từ portal TNUT theo tài khoản và lưu bằng Room để xem offline.
- Sau mỗi lần **đăng nhập thành công**, màn hình chính tự khởi chạy đồng bộ **cả thời khóa biểu và lịch thi** từ portal, sau đó tự kiểm tra mỗi ngày hoặc khi người dùng nhấn tải lại. Hai API chạy độc lập, vì vậy lỗi lịch học không chặn tải lịch thi.
- Không có hai chức năng nhập JSON/CSV/TSV bằng tay; nguồn lịch học và lịch thi mới duy nhất là API TNUT theo phiên đăng nhập.
- Khi đồng bộ lỗi (mạng, HTTP 401), giữ nguyên dữ liệu thật đã tải trước đó; không tạo ra lịch thi giả. Nếu phiên hết hạn, thử đăng nhập lại trong app.
- Những bản cũ đã từng chèn 6 lịch thi mẫu: bản mới chỉ tự dọn nếu cả 6 dòng trong cache khớp chính xác bộ mẫu; các lịch thi thật và ghi chú không bị xóa.
- Màn hình chính hiển thị đủ bảy ngày trong tuần; mở vào ngày hôm nay và tự cuộn đến mục tương ứng, chạm ngày khác để cuộn đến ngày đó. Dải lịch hỗ trợ xem lịch tháng; chạm lại ngày đã chọn để thu gọn. Ngày hiện tại được cập nhật sau nửa đêm.
- Nhắc lịch **hôm nay** và **ngày mai** độc lập trong Cài đặt, mặc định tắt. Người dùng chọn giờ cho từng loại, Android xin quyền thông báo khi cần; lịch nhắc được lên lại sau khi khởi động máy hoặc đổi giờ/múi giờ.
- Thông báo tổng hợp các buổi học/thi từ Room trong học kỳ hiện tại, không gọi mạng nền liên tục. Android có thể giao nhắc muộn hơn thời điểm chọn khi tiết kiệm pin.
- Nếu chưa từng đồng bộ thành công, ứng dụng không thể biết lịch thi chính xác từ một ảnh bảng lịch dùng chung; cần đăng nhập để tải dữ liệu riêng từ portal.

## Kỹ thuật

- Kotlin + Jetpack Compose (Material 3), minSdk 24, targetSdk 35
- Package `com.tkb.sv`, tên hiển thị TKB SV
- Room (SQLite) cho lịch tuần, EncryptedSharedPreferences cho tài khoản/token
- App không cố định một trường: phần chung (UI, Room, đăng nhập) ở `com.sv.tnu`, phần lấy lịch của từng trường nằm trong thư mục riêng — hiện tại chỉ có `com.sv.tnu.tnut` (TNUT). Thêm trường khác = thêm 1 thư mục như `tnut/` rồi đổi điểm gắn trong `MainActivity`.
- Retrofit + kotlinx-serialization gọi API portal (mã TNUT trong `tnut/`):
  - `POST /api/auth/login` (OAuth2 password grant)
  - `POST /api/sch/w-locdshockytkbuser` (danh sách học kỳ)
  - `POST /api/sch/w-locdstkbtuanusertheohocky` (lịch tuần cả học kỳ)
  - `POST /api/epm/w-locdslichthisvtheohocky` (lịch thi giữa kỳ/cuối kỳ, phân trang 100 bản ghi)

## Build

```bash
./gradlew assembleDebug
```

Bản release ký được build tự động bằng GitHub Actions trên mỗi push lên `main`, xem `.github/workflows/build.yml`.

## Tải APK

https://ngocminhvn.github.io/tkb/TKB-SV.apk
