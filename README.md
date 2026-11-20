# TKB SV — Thời khóa biểu TNUT (Android)

Ứng dụng Android mã nguồn mở để xem **thời khóa biểu, lịch thi và ghi chú môn học** cho sinh viên Đại học Kỹ thuật Công nghiệp Thái Nguyên (TNUT).

- Lấy dữ liệu theo phiên đăng nhập **trực tiếp** từ [portal.tnut.edu.vn](https://portal.tnut.edu.vn), không qua API/máy chủ trung gian do dự án vận hành.
- Lưu dữ liệu trong **Rust SQLite** để xem offline; giữ cache cũ khi đồng bộ lỗi. Cài đặt hiển thị nhận diện tài khoản SV TNUT, số buổi thi đã lưu và học kỳ hiện tại.
- Giao diện **Kotlin + Jetpack Compose (Material 3 Expressive 1.5)**, chuyển tab một composition với fade không nảy, tab kính chỉ có một chuyển động, vuốt ngang trên nội dung cả ba tab để chuyển liền kề giống thanh Liquid Glass; bỏ vuốt đổi ngày trên timeline, ngày tròn 36dp, các hàng thao tác đồng bộ tối thiểu 88dp và Liquid Glass giữ nguyên; nghiệp vụ lịch, giao thức dữ liệu TNUT và xử lý gói cập nhật chạy bằng **Rust** qua JNI.
- **Chỉ hỗ trợ Android và TNUT** tại thời điểm tài liệu này được cập nhật. Kiến trúc có interface cho trường khác nhưng chưa bật đổi trường.

> **Dành cho AI/code agent:** Đọc [AGENTS.md](AGENTS.md) trước khi sửa. File này xác định nơi chịu trách nhiệm cho từng tính năng, luồng dữ liệu, các bất biến về SQLite/JNI và bộ test bắt buộc.

Material 3 Expressive **1.5.0 stable** (Compose BOM **2026.11.01**, compileSdk **37.1**) cho giao diện Compose, motion và nút morphing với dáng bo góc nhỏ (12–16dp), phản hồi chạm và LoadingIndicator. **Giữ nguyên hình dạng card, hộp thoại và bố cục của build #395**; không áp dụng bo góc tròn quá mức toàn cục. Liquid Glass nguyên bản vẫn làm nền và thanh điều hướng. Widget và hộp thoại hệ thống Android không thay đổi.

## Tính năng hiện có

- Đăng nhập TNUT, làm mới phiên/token; thông báo rõ khi phiên không còn hợp lệ.
- Lịch học cuộn dọc từ hôm nay (không mở các ngày cũ); tải thêm ngày theo từng nhóm khi cuộn, chọn ngày/tuần và xem lịch tháng.
- Xem môn, tiết/giờ, phòng, giảng viên, lớp/nhóm và ghi chú riêng cho buổi học.
- Đồng bộ lịch học và lịch thi; lịch học chỉ lưu những buổi từ hôm nay trở đi, giữ tuần hiện tại và các tuần tương lai. Xem lịch thi giữa kỳ/cuối kỳ, phân trang dữ liệu portal.
- Lưu lịch học, lịch thi, ghi chú trên thiết bị, sử dụng lại khi không có mạng.
- Khi mở app, hiển thị lịch đã lưu trong lúc đồng bộ. Nếu cache trống và lần tự đồng bộ trước chưa thành công, thử lại một lần trong phiên mở app.
- Nhắc lịch hôm nay/ngày mai theo thiết lập người dùng; widget thời khóa biểu.
- Xem điểm học kỳ, điểm tổng kết hệ 10 / hệ 4, điểm chữ, chi tiết điểm thành phần và điểm tích lũy GPA toàn khóa, lưu cache offline tại tab Thêm.
- Luyện thi trắc nghiệm offline (import bộ đề DOCX, PDF, TXT, JSON) tại tab Thêm.
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
TNUT HTTP/Authentication        Rust SQLite
Retrofit + OkHttp               tkb.db (schema v4)
TnutApiClient                   cache, lịch thi, ghi chú
TnutApi / TnutRepository             ▲
       │                             │
       └─ HTTP response bytes ─→ RustPortalCore (JNI)
                                  └─ rust-core/src/portal.rs
                                     kiểm tra + ánh xạ
                                     └─ binary TKB1 → bản ghi Kotlin

RustScheduleCore (JNI) → rust-core/src/lib.rs
  chuẩn bị timeline theo lô, chuyển tuần, quyết định tự đồng bộ, phân trang, nội dung nhắc và bố cục widget

rust-core/src/update.rs
  xác minh SHA-256 và giải nén gói cập nhật APK
```

Kotlin quản lý giao diện, HTTP/TLS trực tiếp với TNUT, khóa làm mới phiên, Keystore và các API hệ thống Android. Rust tạo phần thân yêu cầu lịch học/lịch thi, đọc phản hồi đăng nhập, ảnh đại diện và lịch, quyết định phân trang, loại trùng và ghi toàn bộ các trang lịch thi theo từng nguồn, ghi lịch hợp lệ vào SQLite và chọn bước làm mới phiên. Bộ cập nhật dùng Rust để đọc manifest phát hành, xác minh SHA-256 và giải nén XZ. Endpoint lịch học lọc theo học kỳ. Rust loại các tuần và buổi học đã qua theo giờ Việt Nam trước khi lưu vào SQLite.

## Bản đồ thư mục (đọc trước khi sửa)

| Đường dẫn | Trách nhiệm chính | Khi nào cần mở? |
| --- | --- | --- |
| `app/` | Module Android, cấu hình build, mã Kotlin/Compose, resources và test JVM | Sửa APK, giao diện, Android integration |
| `app/src/main/java/com/sv/tnu/` | Mã nguồn ứng dụng; `MainActivity.kt` là điểm tạo DB, session và điều hướng | Luồng khởi động/đăng nhập/điều hướng |
| `.../data/` | `AppDatabase.kt`: model, hợp đồng DAO và adapter SQLite Rust; `AuthStore.kt`: token, tài khoản và cài đặt cục bộ | SQLite, offline, lưu ghi chú, thiết lập |
| `.../schools/` | Contract chung `SchoolProvider.kt` và danh sách `SchoolRegistry.kt` | Thêm/chỉnh cách tích hợp trường |
| `.../schools/tnut/` | API TNUT, OkHttp/Retrofit, model, đăng nhập, refresh và đồng bộ lịch vào SQLite | Lỗi portal, token, lịch học/lịch thi |
| `.../rust/` | Adapter Kotlin ↔ Rust JNI; giải mã nhị phân `TKB1` thành database Entity | Thay đổi kiểu dữ liệu / giao thức JNI |
| `.../ui/` | Compose, ViewModel, lịch tuần/ngày, login, cài đặt, chọn ngày, dialog cập nhật | Giao diện, cuộn/chuyển ngày, trạng thái tải |
| `.../ui/components/` | Thành phần giao diện dùng lại, ví dụ avatar | Sửa UI tái sử dụng |
| `.../ui/glass/` | Hiệu ứng Liquid Glass trong UI | Sửa hiệu ứng hiển thị |
| `.../ui/theme/` | Theme và màu Compose | Sáng/tối, màu sắc |
| `.../notifications/` | Lên lịch và hiển thị nhắc lịch, receiver khởi động lại | Thông báo hôm nay/ngày mai |
| `.../widget/` | Widget Android/RemoteViews, chuyển ngày, đọc dữ liệu SQLite | Widget ngoài màn hình chính |
| `.../update/` | Kiểm tra, tải, xác thực và cài APK cập nhật | Cơ chế cập nhật ứng dụng |
| `app/src/main/res/` | Drawable, XML widget/layout, string, color, theme Android | Tài nguyên đồ họa/XML |
| `rust-core/` | Rust crate, Cargo manifest/lock và nghiệp vụ độc lập Android | Thay đổi xử lý dữ liệu Rust |
| `rust-core/src/lib.rs` | Ngày tháng, lịch tuần, lịch tháng, giờ học/thi, helper thuần Rust | Logic thời gian |
| `rust-core/src/portal.rs` | Kiểm tra và ánh xạ JSON TNUT, phân trang/chống trùng, tạo binary `TKB1` | Sai dữ liệu lịch, lịch thi |
| `rust-core/src/update.rs` | Kiểm tra SHA-256 rồi giải nén hoặc chép APK đã xác thực | Tải và chuẩn bị cập nhật |
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
2. SQLite Rust dùng `tkb.db`, **schema version 4**, có migration 1→2→3→4. Không dùng destructive migration, không xóa ghi chú/cache người dùng khi đồng bộ lỗi.
3. Kiểm tra dữ liệu portal **trước** transaction thay lịch. Hai nguồn lịch học và lịch thi độc lập; lỗi một nguồn không được xóa dữ liệu nguồn kia.
4. Android dùng JNI Rust với kết quả nhị phân `TKB1` (không còn vòng JSON → Rust → JSON); nếu sửa encoder/decoder phải cập nhật **cả hai phía**.
5. Trường thứ hai chưa được bật vì cache/tài khoản chưa tách theo trường và sinh viên. Đọc [docs/ADDING_SCHOOL.md](docs/ADDING_SCHOOL.md).
6. Không đưa mật khẩu, token, thông tin sinh viên hoặc keystore lên log, issue hay tài liệu.

## Build

Database hiện chạy qua `rusqlite` trong `rust-core/src/database/`, JNI batch
`TKB1T/TKB1E/TKB1N/TKB1C` và `RustCacheStore` triển khai toàn bộ `TkbDao`.
Giữ `tkb.db`, schema v4; Kotlin Flow nhận cập nhật sau commit.
Rust SQLite mở trực tiếp cơ sở dữ liệu `tkb.db` của các bản dùng Room trước đây.
Xem [tiến độ và giới hạn kiểm thử](docs/RUST_MIGRATION.md).

Yêu cầu: JDK 17, Android SDK/NDK tương thích, Rust toolchain và `cargo-ndk`. Đứng tại thư mục gốc repository:

```bash
# Kiểm tra mã Rust
cargo test --locked --manifest-path rust-core/Cargo.toml
cargo clippy --locked --manifest-path rust-core/Cargo.toml --all-targets -- -D warnings

# Biên dịch thư viện Rust cho arm64-v8a, armeabi-v7a, x86_64, x86
cargo install cargo-ndk --locked
bash scripts/build-rust-android.sh

# Build APK Release
./gradlew assembleRelease
```

`app/build.gradle.kts` **chặn build** nếu thiếu `app/src/main/jniLibs/<ABI>/libtkb_core.so`. Thư mục `jniLibs` chứa đầu ra được tạo từ script, không phải nguồn Rust cần sửa trực tiếp. Xem thêm [rust-core/README.md](rust-core/README.md).

### CI/phát hành

- [`build.yml`](.github/workflows/build.yml): chạy khi push `main` hoặc bấm chạy tay; build Rust 4 ABI, build Release có R8, kiểm tra chữ ký, xuất APK và dữ liệu cập nhật, deploy GitHub Pages.

**APK hiện được cấu hình phát hành tại:** https://ngocminhvn.github.io/tkb/TKB-SV.apk

**Chú ý:** sửa tài liệu rồi push `main` cũng kích hoạt workflow `build.yml` theo cấu hình hiện tại. Không mặc định coi build thành công khi chưa kiểm tra Actions.

## Bảo mật và giới hạn

Ứng dụng sử dụng portal TNUT làm nguồn dữ liệu tài khoản; credentials/token nằm trong bộ nhớ được bảo vệ trên thiết bị qua `EncryptedSharedPreferences`, lịch và ghi chú nằm trong SQLite. Không có dịch vụ trung gian do dự án vận hành để sao chép dữ liệu lịch. Việc mở rộng sang trường khác cần thiết kế cách ly tài khoản, cache và migration trước khi cho phép chuyển trường.

## Đóng góp / giao việc cho AI

Khi nhờ AI chỉnh repo, đưa yêu cầu cụ thể và yêu cầu đọc [AGENTS.md](AGENTS.md). AI cần chỉ rõ **đường đi dữ liệu, file dự định sửa, test liên quan, rủi ro đối với cache/login/JNI và kết quả build thực tế**; tránh đổi kiến trúc hoặc viết lại hàng loạt nếu chỉ cần sửa một hành vi.

## Ôn tập trắc nghiệm offline

Từ tab **Thêm → Ôn tập**, người dùng có thể import **DOCX, PDF có lớp văn bản, TXT và JSON**, xem trước/chỉnh đáp án, rồi lưu bộ đề và luyện tập không cần Internet. DOCX nhận trực tiếp các lựa chọn có chữ đỏ (định dạng `w:color` trong run); TXT/PDF nhận đáp án dạng `Đáp án: A, C`; JSON nhận `questions[]`, `options[]` và `answer` / `correct`. PDF scan không có lớp chữ cần OCR và không hỗ trợ trong phiên bản này; PDF có chữ màu đỏ hiện **chỉ trích xuất chữ, chưa nhận được màu**.

- Các câu thiếu đáp án bắt buộc xác nhận/chọn đáp án trước khi lưu.
- Bộ đề, câu trả lời gần nhất, điểm số và câu sai được lưu **riêng** trong `quiz.db` (SQLite Android), không thay đổi `tkb.db` schema v4.
- `ui/QuizScreen.kt`: thư viện đề, chọn file, xem trước, làm bài, ôn câu sai, xóa đề.
- `quiz/QuizImporter.kt`: SAF + chuyển thẳng byte DOCX/PDF/TXT/JSON cho Rust xử lý; giới hạn tệp 30 MB.
- `rust-core/src/quiz.rs` + `rust/RustQuizCore.kt`: trích xuất văn bản PDF bằng pure Rust `pdf-extract`, parse DOCX/XML/TXT/JSON, lọc chữ đỏ, wire format nhị phân TKB1, quản lý SQLite quiz.db qua rusqlite.
- Đã loại bỏ hoàn toàn dependency Java `pdfbox-android`; toàn bộ quy trình đọc tệp, trích xuất text, parse và lưu trữ đều do Rust xử lý.
- Không có endpoint upload đề hoặc API OCR; tệp chỉ được xử lý cục bộ khi người dùng chọn.

Dạng TXT/PDF văn bản ví dụ:

```text
Câu 1: Thủ đô Việt Nam là đâu?
A. Huế
B. Hà Nội
C. Đà Nẵng
Đáp án: B
```

Với JSON, chỉ số đáp án dạng số là **0-based**, ký tự là **A/B/C/...**.


### DOCX nhiều dòng

Trình nhập DOCX tách nhãn A/B/C/D kể cả khi nhãn đứng trên một dòng riêng và lựa chọn là nhiều dòng C++, cũng như trường hợp nhiều lựa chọn gộp trong một đoạn, đáp án không đúng thứ tự hoặc câu hỏi chỉ có số (\`491.\`). Dấu màu đỏ được tính theo từng run định dạng OOXML chứ không theo màu chung của cả đoạn. Một số nhãn "a." được Word lưu ở \`w:numPr\` thay vì văn bản cũng được khôi phục. Câu không có màu đỏ cần được xác nhận thủ công; nút lọc câu thiếu đáp án trong màn hình xem trước giúp sửa nhanh. Khi file gốc tô đỏ nhiều lựa chọn, ứng dụng hiển thị cảnh báo để người dùng kiểm tra.

### MD3 Expressive experimental controls (revert-safe)
- Compose TimePickerDialog 24h cho giờ nhắc hôm nay/ngày mai, chuyển đồng hồ/bàn phím; lưu dữ liệu vào AuthStore và gọi ScheduleReminders.syncAll giống trước.
- Connected ToggleButton group cho chế độ Sáng/Tối, vẫn lưu trong AuthStore; chiều cao nút 40dp.
- SplitButton Import nhanh ở màn Ôn tập cho chọn mọi file, PDF hoặc Word; bộ đọc QuizImporter và ô import cũ giữ nguyên.
- Các thay đổi thử nghiệm nằm riêng một commit, có thể git revert mà không ảnh hưởng commit vuốt tab hoặc thẻ Cài đặt gọn 72dp.
