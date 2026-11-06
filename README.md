# SvTNU

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

## Dữ liệu mẫu

`app/src/main/assets/sample_tkb_tuan_20261.json` là thời khóa biểu thật của học kỳ 20261 (HK1 2026–2027, 15 tuần, 98 buổi) dùng để seed Room lần đầu mở app khi chưa có dữ liệu. Sau khi đăng nhập, app tải lịch thật của bạn và ghi đè dữ liệu mẫu.

## Kỹ thuật

- Kotlin + Jetpack Compose (Material 3), minSdk 24, targetSdk 35
- Room (SQLite) cho lịch tuần, EncryptedSharedPreferences cho tài khoản/token
- Retrofit + kotlinx-serialization gọi API portal:
  - `POST /api/auth/login` (OAuth2 password grant)
  - `POST /api/sch/w-locdshockytkbuser` (danh sách học kỳ)
  - `POST /api/sch/w-locdstkbtuanusertheohocky` (lịch tuần cả học kỳ)

## Build

```bash
./gradlew assembleDebug
```

Bản release ký được build tự động bằng GitHub Actions trên mỗi push lên `main`, xem `.github/workflows/build.yml`.

## Tải APK

https://ngocminhvn.github.io/tkb/SvTNU.apk
