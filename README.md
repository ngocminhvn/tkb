# TKB SV — Thời khóa biểu TNUT (Android)

Ứng dụng Android mã nguồn mở để xem **thời khóa biểu, lịch thi và ghi chú môn học** cho sinh viên Đại học Kỹ thuật Công nghiệp Thái Nguyên (TNUT).

- Lấy dữ liệu theo phiên đăng nhập **trực tiếp** từ [portal.tnut.edu.vn](https://portal.tnut.edu.vn), không qua API/máy chủ trung gian do dự án vận hành.
- Lưu dữ liệu trong **Room/SQLite** để xem offline; giữ cache cũ khi đồng bộ lỗi.
- Giao diện **Kotlin + Jetpack Compose (Material 3)**; một phần xử lý dữ liệu lịch chạy bằng **Rust** qua JNI.
- **Chỉ hỗ trợ Android và TNUT** tại thời điểm tài liệu này được cập nhật. Kiến trúc có interface cho trường khác nhưng chưa bật đổi trường.

> **Dành cho AI/code agent:** Đọc [AGENTS.md](AGENTS.md) trước khi sửa. File này xác định nơi chịu trách nhiệm cho từng tính năng, luồng dữ liệu, các bất biến về Room/JNI và bộ test bắt buộc.

## Tính năng hiện có

- Đăng nhập TNUT, làm mới phiên/token; thông báo rõ khi phiên không còn hợp lệ.
- Lịch học cuộn dọc từ hôm nay (không mở các ngày cũ); tải thêm ngày theo từng nhóm khi cuộn, chọn ngày/tuần và xem lịch tháng.
- Xem môn, tiết/giờ, phòng, giảng viên, lớp/nhóm và ghi chú riêng cho buổi học.
- Đồng bộ lịch học và lịch thi; lịch học chỉ lưu những buổi từ hôm nay trở đi, giữ tuần hiện tại và các tuần tương lai. Xem lịch thi giữa kỳ/cuối kỳ, phân trang dữ liệu portal.
- Lưu lịch học, lịch thi, ghi chú trên thiết bị, sử dụng lại khi không có mạng.
- Nhắc lịch hôm nay/ngày mai theo thiết lập người dùng; widget thời khóa biểu.
- Chế độ giao diện sáng/tối và kiểm tra/cài đặt bản cập nhật ứng dụng.

## Sơ đồ kiến trúc

```text
Android UI (Compose)
  ├─ MainActivity → SchoolRegistry → SchoolSession
  ├─ LoginScreen / TimetableViewModel / SettingsScreen
  └─ quan sát SchoolRepository (Flow)
                      │
       ┌──────────────┴──────────────┐
       │                             │
TNUT HTTP/Authentication        Room/SQLite
Retrofit + OkHttp               tkb.db (schema v4)
TnutApiClient                   cache, lịch thi, ghi chú
TnutApi / TnutRepository             ▲
       │                             │
       └─ HTTP response bytes ─→ RustPortalCore (JNI)
                                  └─ rust-core/src/portal.rs
                                     kiểm tra + ánh xạ
                                     └─ binary TKB1 → Room entities

RustScheduleCore (JNI) → rust-core/src/lib.rs
  tính toán ngày/tuần, thời gian và chuẩn hóa dữ liệu
```

**Phân chia trách nhiệm:** Kotlin vẫn giữ HTTP, làm mới token, Android lifecycle, truy cập Room, giao diện, thông báo, widget và cập nhật APK. Rust xử lý logic độc lập Android, chỉ mã hóa các tuần/buổi học từ ngày hiện tại theo giờ Việt Nam vào kết quả nhị phân TKB1. **Chưa phải ứng dụng 100% Rust.** Endpoint TNUT hiện được tích hợp theo bộ lọc học kỳ; chưa xác minh được tham số lọc từ ngày phía máy chủ, nên HTTP vẫn nhận kết quả học kỳ đầy đủ rồi Rust mới loại dữ liệu quá khứ trước khi chuyển sang Kotlin/Room.

## Bản đồ thư mục (đọc trước khi sửa)

| Đường dẫn | Trách nhiệm chính | Khi nào cần mở? |
| --- | --- | --- |
| `app/` | Module Android, cấu hình build, mã Kotlin/Compose, resources và test JVM | Sửa APK, giao diện, Android integration |
| `app/src/main/java/com/sv/tnu/` | Mã nguồn ứng dụng; `MainActivity.kt` là điểm tạo DB, session và điều hướng | Luồng khởi động/đăng nhập/điều hướng |
| `.../data/` | `AppDatabase.kt`: Entity/DAO/migration Room; `AuthStore.kt`: token, tài khoản và cài đặt cục bộ | SQLite, offline, lưu ghi chú, thiết lập |
| `.../schools/` | Contract chung `SchoolProvider.kt` và danh sách `SchoolRegistry.kt` | Thêm/chỉnh cách tích hợp trường |
| `.../schools/tnut/` | API TNUT, OkHttp/Retrofit, model, đăng nhập, refresh và đồng bộ lịch vào Room | Lỗi portal, token, lịch học/lịch thi |
| `.../rust/` | Adapter Kotlin ↔ Rust JNI; giải mã nhị phân `TKB1` thành Room Entity | Thay đổi kiểu dữ liệu / giao thức JNI |
| `.../ui/` | Compose, ViewModel, lịch tuần/ngày, login, cài đặt, chọn ngày, dialog cập nhật | Giao diện, cuộn/chuyển ngày, trạng thái tải |
| `.../ui/components/` | Thành phần giao diện dùng lại, ví dụ avatar | Sửa UI tái sử dụng |
| `.../ui/glass/` | Hiệu ứng Liquid Glass trong UI | Sửa hiệu ứng hiển thị |
| `.../ui/theme/` | Theme và màu Compose | Sáng/tối, màu sắc |
| `.../notifications/` | Lên lịch và hiển thị nhắc lịch, receiver khởi động lại | Thông báo hôm nay/ngày mai |
| `.../widget/` | Widget Android/RemoteViews, chuyển ngày, đọc dữ liệu Room | Widget ngoài màn hình chính |
| `.../update/` | Kiểm tra, tải, xác thực và cài APK cập nhật | Cơ chế cập nhật ứng dụng |
| `app/src/main/res/` | Drawable, XML widget/layout, string, color, theme Android | Tài nguyên đồ họa/XML |
| `rust-core/` | Rust crate, Cargo manifest/lock và nghiệp vụ độc lập Android | Thay đổi xử lý dữ liệu Rust |
| `rust-core/src/lib.rs` | Ngày tháng, lịch tuần, lịch tháng, giờ học/thi, helper thuần Rust | Logic thời gian |
| `rust-core/src/portal.rs` | Kiểm tra và ánh xạ JSON TNUT, phân trang/chống trùng, tạo binary `TKB1` | Sai dữ liệu lịch, lịch thi |
| `rust-core/src/android.rs` | Hàm JNI export cho ứng dụng Android | Chữ ký hàm JNI, giao tiếp native |
| `scripts/` | `build-rust-android.sh` tạo thư viện `libtkb_core.so` cho 4 ABI | Biên dịch thư viện Rust Android |
| `.github/workflows/build.yml` | Build Rust 4 ABI, đóng gói APK Release có chữ ký và triển khai GitHub Pages | Build Actions, đóng gói, phát hành |
| `gradle/` | Gradle wrapper và version catalog `libs.versions.toml` | Thay phiên bản dependencies |
| `docs/` | Tài liệu hướng dẫn kiến trúc mở rộng, hiện có `ADDING_SCHOOL.md` | Thêm trường mới |
| `AGENTS.md` | Chỉ dẫn cho AI: bản đồ file, ràng buộc, chuỗi phụ thuộc | **Đọc trước mọi thay đổi** |

`.../` trong bảng là viết tắt của `app/src/main/java/com/sv/tnu/`. Sơ đồ trên phản ánh **mã nguồn hiện tại**, không phải kiến trúc dự kiến.

## Tra nhanh: sửa tính năng nào ở đâu?

| Mục tiêu | File nên đọc/sửa trước |
| --- | --- |
| Cuộn ngày, chuyển tuần, chọn ngày trên lịch | `ui/TimetableScreen.kt`, `ui/WeekDatePicker.kt` |
| Dữ liệu hiển thị và trạng thái đồng bộ trên màn hình chính | `ui/TimetableScreen.kt` (TimetableViewModel), `ui/MainScreen.kt` |
| Đăng nhập, token hết hạn / HTTP 401 | `schools/tnut/TnutApiClient.kt`, `TnutSchoolProvider.kt`, `ui/LoginScreen.kt`, `data/AuthStore.kt` |
| Dữ liệu lịch học/lịch thi trả sai hoặc trùng | `schools/tnut/TnutRepository.kt`, `rust/RustPortalCore.kt`, `rust-core/src/portal.rs` |
| Tải chậm, parser, thời gian/ngày tháng | `rust-core/src/lib.rs`, `portal.rs`, `ui/TimetableScreen.kt` |
| Ghi chú, lịch offline, migration | `data/AppDatabase.kt`, `schools/tnut/TnutRepository.kt` |
| Nhắc lịch hoặc widget | `notifications/ScheduleReminders.kt`, `widget/TimetableWidgetProvider.kt` |
| Giao diện, màu, hiệu ứng | `ui/`, `ui/glass/`, `ui/theme/`, `res/` |
| Cập nhật APK / GitHub Pages | `update/`, `.github/workflows/build.yml` |

Các đường dẫn rút gọn trong bảng tính từ `app/src/main/java/com/sv/tnu/`, trừ `rust-core/` và `.github/`.

## Lưu ý kỹ thuật không được phá vỡ

1. `applicationId = com.tkb.sv`, namespace Kotlin `com.sv.tnu`; minSdk 24, targetSdk 35. Đổi ID hoặc chữ ký sẽ ảnh hưởng khả năng cập nhật APK.
2. Room dùng `tkb.db`, **schema version 4**, có migration 1→2→3→4. Không dùng destructive migration, không xóa ghi chú/cache người dùng khi đồng bộ lỗi.
3. Kiểm tra dữ liệu portal **trước** transaction thay lịch. Hai nguồn lịch học và lịch thi độc lập; lỗi một nguồn không được xóa dữ liệu nguồn kia.
4. Android dùng JNI Rust với kết quả nhị phân `TKB1` (không còn vòng JSON → Rust → JSON); nếu sửa encoder/decoder phải cập nhật **cả hai phía**.
5. Trường thứ hai chưa được bật vì cache/tài khoản chưa tách theo trường và sinh viên. Đọc [docs/ADDING_SCHOOL.md](docs/ADDING_SCHOOL.md).
6. Không đưa mật khẩu, token, thông tin sinh viên hoặc keystore lên log, issue hay tài liệu.

## Build

Yêu cầu: JDK 17, Android SDK/NDK tương thích, Rust toolchain và `cargo-ndk`. Đứng tại thư mục gốc repository:

```bash
# Kiểm tra mã Rust
cargo clippy --manifest-path rust-core/Cargo.toml --all-targets -- -D warnings

# Biên dịch thư viện Rust cho arm64-v8a, armeabi-v7a, x86_64, x86
cargo install cargo-ndk --locked
bash scripts/build-rust-android.sh

# Build APK debug
./gradlew assembleDebug
```

`app/build.gradle.kts` **chặn build** nếu thiếu `app/src/main/jniLibs/<ABI>/libtkb_core.so`. Thư mục `jniLibs` chứa đầu ra được tạo từ script, không phải nguồn Rust cần sửa trực tiếp. Xem thêm [rust-core/README.md](rust-core/README.md).

### CI/phát hành

- [`build.yml`](.github/workflows/build.yml): chạy khi push `main` hoặc bấm chạy tay; build Rust 4 ABI, build Release có R8, kiểm tra chữ ký, xuất APK và dữ liệu cập nhật, deploy GitHub Pages.

**APK hiện được cấu hình phát hành tại:** https://ngocminhvn.github.io/tkb/TKB-SV.apk

**Chú ý:** sửa tài liệu rồi push `main` cũng kích hoạt workflow `build.yml` theo cấu hình hiện tại. Không mặc định coi build thành công khi chưa kiểm tra Actions.

## Bảo mật và giới hạn

Ứng dụng sử dụng portal TNUT làm nguồn dữ liệu tài khoản; credentials/token nằm trong bộ nhớ được bảo vệ trên thiết bị qua `EncryptedSharedPreferences`, lịch và ghi chú nằm trong Room. Không có dịch vụ trung gian do dự án vận hành để sao chép dữ liệu lịch. Việc mở rộng sang trường khác cần thiết kế cách ly tài khoản, cache và migration trước khi cho phép chuyển trường.

## Đóng góp / giao việc cho AI

Khi nhờ AI chỉnh repo, đưa yêu cầu cụ thể và yêu cầu đọc [AGENTS.md](AGENTS.md). AI cần chỉ rõ **đường đi dữ liệu, file dự định sửa, test liên quan, rủi ro đối với cache/login/JNI và kết quả build thực tế**; tránh đổi kiến trúc hoặc viết lại hàng loạt nếu chỉ cần sửa một hành vi.
