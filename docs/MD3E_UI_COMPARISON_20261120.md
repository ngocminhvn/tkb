# TKB SV — Kiểm tra hồi quy UI MD3 → MD3 Expressive (20/11/2026)

## Phạm vi và nguồn đối chiếu

- **MD3 cũ:** commit `4541aab5fd803c1c8a7c369d6421eee1a55e2b6e`, ngay trước đợt chuyển sang Expressive.
- **MD3E trước khi sửa:** commit `91ec49c9cdb5744002dc8b56acb7e63529564553`, bản build #400.
- **Đầu vào đánh giá:** ảnh chụp bốn màn hình và video chuyển tab do người dùng cung cấp ngày 20/11/2026.
- **Giới hạn:** rà soát mã nguồn và kiểm tra build Release; cảm giác FPS/sự mượt trên phần cứng cần kiểm tra trên điện thoại.

## So sánh kỹ thuật

| Hạng mục | MD3 cũ | MD3E sau chỉnh |
| --- | --- | --- |
| Compose BOM | 2024.09.00 | 2026.11.01 |
| Material 3 | Từ BOM, chưa ép 1.5.0 | Phiên bản 1.5.0 |
| Kotlin / AGP | 2.0.21 / 8.5.2 | 2.3.10 / 9.2.1 |
| compileSdk | 35 | Android 37.1; targetSdk vẫn 35 |
| Theme | MaterialTheme | MaterialExpressiveTheme, MotionScheme.expressive() |
| Chuyển tab | Thay nội dung trực tiếp, không ghép hai màn | Một màn duy nhất với fade 8% + trượt lớp vẽ 14dp, dùng fast motion specs; không chạy AnimatedContent toàn màn |
| Liquid Glass | Một viên kính trượt bên dưới tab | Vẫn một viên kính trượt; bỏ nền xanh thứ hai bao icon |
| Ô ngày đang chọn | CircleShape 36dp | CircleShape 36dp, scale chọn rất nhẹ, giữ chấm xanh lá hôm nay |
| Card mục Thêm | Góc 18dp, icon container 44dp, icon 24dp, titleMedium | Khôi phục đúng các thông số này; giữ phản hồi chạm Expressive |
| Card mục Cài đặt | Nhóm thẻ bo 18dp, icon 40dp, khoảng cách 16dp | Bo 18dp, icon 36dp, cách nhau 12dp, padding dọc 10dp thống nhất |
| Loading | CircularProgressIndicator truyền thống | LoadingIndicator/ContainedLoadingIndicator dạng MD3E, chỉ định màu primary xanh #0A84FF |
| Màu loading trên nút xanh | Spinner trắng tương phản | Tiếp tục trắng trên nút xanh để bảo đảm độ tương phản; loading đứng riêng dùng xanh |

## Vấn đề được xử lý

1. Hai màn hình được compose cùng lúc trong AnimatedContent làm tăng tải layout khi đổi giữa TKB, Thêm và Cài đặt; chuyển sang một nội dung duy nhất và chỉ animate graphics layer.
2. GlassTabBar đã có lens; MainScreen thêm nền xanh trong icon gây chồng lớp. Loại bỏ lớp thứ hai, giữ màu icon xanh khi chọn.
3. Ô ngày 38dp bo góc 12dp gây cảm giác vuông; về hình tròn 36dp.
4. Card mục Thêm đã phình to thành icon 52dp / góc 28dp / titleLarge; về icon 44dp / góc 18dp / titleMedium.
5. Card phần Tiện ích & Cài đặt có khoảng hở và padding lớn; chuẩn hóa thông số gọn, cùng chiều cao tối thiểu theo nội dung.
6. Loading không theo màu chủ đạo; chỉ định `MaterialTheme.colorScheme.primary` cho spinner độc lập và progress cập nhật.

## Các điều không thay đổi

- `ui/glass/LiquidGlass.kt` và cơ chế drag/hold của GlassTabBar.
- Database Rust/SQLite, đồng bộ, lịch thi, ghi chú, lưu bài trắc nghiệm, đăng nhập, API TNUT.
- `applicationId`, targetSdk và chữ ký APK.
- Các chức năng khác và widget Android.

## Kiểm thử

- Kiểm tra tĩnh: không còn AnimatedContent toàn màn, không còn nền xanh trùng, ngày chọn tròn 36dp, card Thêm 18dp/44dp, loading lấy màu primary.
- GitHub Actions: build Release, R8, chữ ký APK, 4 ABI Rust JNI. Kết quả xem tại trang Actions của commit sửa lỗi.
- Cần xác minh thủ công: đổi tab nhanh liên tục, cuộn TKB khi mở lịch tháng, dark/light, bật/tắt nhắc lịch, loading đồng bộ/import, và chạm/kéo lens trong thanh tab.
