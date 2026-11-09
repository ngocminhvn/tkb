# AGENTS.md — Hướng dẫn đọc và sửa repository TKB SV cho AI

## 0. Quy tắc bắt đầu

Tài liệu này áp dụng cho **toàn bộ repository `ngocminhvn/tkb`**. Trước khi chỉnh sửa:

1. Đọc `README.md`, sau đó tìm module bằng **bản đồ thư mục** và **bảng định tuyến công việc** bên dưới.
2. Mở các file **thực tế** ở nhánh đang làm. File này chỉ mô tả baseline; **code mới nhất là nguồn sự thật**.
3. Truy vết đường dữ liệu từ UI → provider/repository → HTTP hoặc Room → Rust/JNI (nếu có), rồi mới sửa.
4. Sửa phạm vi nhỏ nhất có thể; giữ nguyên hành vi khác và dữ liệu của người dùng. Không tự ý chuyển đổi UI sang Rust hoặc thay thế SQLite.
5. Sau khi sửa, chạy những test thích hợp; ghi rõ test nào **đã chạy**, **chưa chạy** và lý do. Không báo build/pass nếu chưa xác minh.
6. Khi sửa một đường dẫn, cập nhật hướng dẫn tương ứng trong `README.md`/`AGENTS.md` để agent kế tiếp không dựa vào tài liệu lỗi thời.

### Những điều bắt buộc giữ nguyên, trừ khi yêu cầu có nêu rõ và đã có phương án migrate/test

- Android `applicationId = "com.tkb.sv"`; Kotlin namespace `com.sv.tnu`; chữ ký Release và khả năng cài đè.
- Room `tkb.db`, schema **v4**, migrations 1→2→3→4; ghi chú, lịch học, lịch thi và cache offline đã lưu.
- Tài khoản/token lưu cục bộ bằng `EncryptedSharedPreferences`; **không log hoặc gửi** password, access/refresh token sang endpoint khác.
- HTTP đi trực tiếp portal TNUT; không chèn proxy/máy chủ trung gian thu thập thông tin đăng nhập.
- Hợp đồng JNI, thư viện `libtkb_core.so` cho 4 ABI, format nhị phân **`TKB1`** thống nhất cả Rust/Kotlin.
- Lịch học và lịch thi có cơ chế đồng bộ riêng; lỗi network/401/JSON rỗng không được tự động xóa cache hợp lệ.
- Chưa bật đổi trường: trước tiên phải cách ly dữ liệu theo `schoolId + accountId` và migrate tương thích.
- Không làm lộ, thay thế hoặc tự đăng tải material ký APK/keystore. Không sử dụng destructive migration để "fix nhanh".

## 1. Bản đồ thư mục thực tế

Gốc Kotlin dưới `app/src/main/java/com/sv/tnu/`. Các đường dẫn bắt đầu bằng `.../` bên dưới được tính từ thư mục này.

| Thư mục/file | Sở hữu trách nhiệm | Không nên đặt logic gì ở đây? |
| --- | --- | --- |
| `app/src/main/java/com/sv/tnu/MainActivity.kt` | Entry point, khởi tạo AuthStore/Room, tạo SchoolSession, NavHost/login/main | Mapping JSON portal, SQL migration |
| `.../data/AppDatabase.kt` | Room Entity/DAO, `CachedTimetable`, giao dịch thay lịch/thi, migration `tkb.db` | Network/Compose |
| `.../data/AuthStore.kt` | Preferences mã hóa, session tokens, học kỳ, thiết lập giao diện/nhắc lịch/đồng bộ | Gọi trực tiếp portal |
| `.../schools/SchoolProvider.kt` | Interface `SchoolProvider`, `SchoolSession`, `SchoolRepository`, ID/model chung | Hardcode UI TNUT |
| `.../schools/SchoolRegistry.kt` | Danh sách school providers, chọn provider theo ID | Quy tắc SQL/HTTP |
| `.../schools/tnut/TnutApi.kt` | Định nghĩa Retrofit endpoint và request signature | Compose, chỉnh DB |
| `.../schools/tnut/TnutApiClient.kt` | OkHttp/Retrofit setup; auth header, HTTP 401 và logical 401, khóa refresh, fallback login | View/layout |
| `.../schools/tnut/TnutModels.kt` | DTO của API TNUT cho login/học kỳ/avatar/request | Dữ liệu UI Compose |
| `.../schools/tnut/TnutSchoolProvider.kt` | Adapter kết nối login/session + repository TNUT | Logic trường khác |
| `.../schools/tnut/TnutRepository.kt` | Điều phối gọi portal, đồng bộ lịch/thi, validate qua Rust, ghi Room, avatar, ghi chú | Vẽ UI và thay định nghĩa JNI trực tiếp |
| `.../rust/RustPortalCore.kt` | JNI Android bridge nhận raw HTTP bytes; `PortalBinaryDecoder` đọc `TKB1` thành Entity | Retrofit, Compose |
| `.../rust/RustScheduleCore.kt` | Wrapper JNI gọi trực tiếp `libtkb_core.so` cho ngày/tuần/tháng/giờ | Room transaction |
| `.../ui/LoginScreen.kt` | Compose login, `LoginViewModel` và thông báo đăng nhập | OkHttp interceptor |
| `.../ui/MainScreen.kt` | Khung màn hình chính, tab/navigation, mở cài đặt, kết nối update | TNUT parsing |
| `.../ui/TimetableScreen.kt` | `TimetableViewModel`, snapshot tuần/lịch/thi, Compose list, state chọn ngày, scroll, gesture, thẻ buổi học và ghi chú | HTTP transport và schema DB |
| `.../ui/WeekDatePicker.kt` | Thanh ngày, chọn ngày/tuần, lịch tháng, thao tác kéo/chạm | Lấy token/API |
| `.../ui/SettingsScreen.kt` | Compose cài đặt giao diện, nhắc lịch, tài khoản, kiểm tra cập nhật (đã bỏ chỉnh số tuần) | SQL migration |
| `.../ui/UpdateDialog.kt` | UI hộp thoại cập nhật APK | Tải file HTTP |
| `.../ui/components/UserAvatar.kt` | Composable avatar dùng lại | Auth protocol |
| `.../ui/glass/LiquidGlass.kt` | Hiệu ứng/liquid glass trên Compose | Nghiệp vụ portal |
| `.../ui/theme/Theme.kt` | Theme Compose | Xử lý lịch |
| `.../notifications/ScheduleReminders.kt` | AlarmManager, thông báo, receivers lên lịch lại sau reboot/time change | Đồng bộ portal trong background |
| `.../widget/TimetableWidgetProvider.kt` | AppWidgetProvider/RemoteViews, widget ngày & query Room | Màn hình Compose |
| `.../update/AppUpdateManager.kt` | Metadata cập nhật, tải/xác minh, cài APK | UI state |
| `.../update/AppUpdateViewModel.kt` | Trạng thái tải/kiểm tra update cho Compose | Portal TNUT |
| `app/src/main/res/drawable/` | Icon, nền, hình XML | UI ViewModel |
| `app/src/main/res/layout/` | Layout XML dành cho widget | Compose timetable chính |
| `app/src/main/res/values*/` | String, color, theme XML | Logic dữ liệu |
| `app/src/main/res/xml/` | Cấu hình widget và FileProvider | Nghiệp vụ lịch |
| `app/src/main/AndroidManifest.xml` | Permission, Activity, FileProvider, notification/widget receivers | SQL |
| `rust-core/src/lib.rs` | Pure Rust: chuẩn hóa ngày, tuần 7 ngày, lịch tháng, đổi ngày, thời gian học/thi | JNI/Android Context |
| `rust-core/src/portal.rs` | Parse/validate payload TNUT, chống trùng, phân trang, ánh xạ và encode `TKB1` | Gọi mạng, mật khẩu |
| `rust-core/src/android.rs` | `extern "system"` JNI entrypoints tương ứng wrapper Kotlin | UI, persistence |
| `rust-core/Cargo.toml`, `Cargo.lock` | Cấu hình & khóa dependency Rust | Android UI |
| `scripts/build-rust-android.sh` | Cargo NDK build 4 ABI, đặt `.so` vào `jniLibs` | Business logic |
| `.github/workflows/build.yml` | Pipeline push main → Rust/JNI 4 ABI → signed Release APK → GitHub Pages | Nghiệp vụ app |
| `gradle/` / `build.gradle.kts` / `settings.gradle.kts` | Gradle wrapper, catalog, module/config | Logic ngày/tuần |
| `docs/ADDING_SCHOOL.md` | Các bước thêm school adapter và migration dữ liệu | Tắt giới hạn cách ly |
| `tkb-release.keystore.b64` | Material liên quan chữ ký bản phát hành: **nhạy cảm** | Không đọc/in/dán nội dung ra câu trả lời |

## 2. Định tuyến tác vụ: yêu cầu → file → test

| Yêu cầu người dùng | Bắt đầu ở | Kiểm tra kéo theo |
| --- | --- | --- |
| Cuộn đến ngày nào thì thanh tuần chọn ngày đó, kéo qua ranh giới tuần, đổi tuần | `ui/TimetableScreen.kt` (`TimetableScreen`, state/gesture/scroll), `ui/WeekDatePicker.kt` | `TimetableSwipeTest.kt`, `WeekDatePickerTest.kt`; kéo ở mép >144dp, thả tay xác nhận; kéo ngược hủy; kiểm thử tay cả đầu/cuối tuần và danh sách rỗng |
| Sửa màn đầu không hiện lịch, lag khi mở, chuyển tab mới hiện | `ui/TimetableScreen.kt` (`TimetableViewModel`, `cachedTimetable`, `timetable StateFlow`), `data/AppDatabase.kt` | Mất mạng, lần mở đầu, cold start, cache cũ, quay lại tab |
| Sửa lỗi đồng bộ và hộp thoại trạng thái | `ui/TimetableScreen.kt` (ViewModel), `ui/TimetableSyncDialog.kt`, `ui/MainScreen.kt`, `schools/tnut/TnutRepository.kt` | Auto/manual sync hiển thị Loading/Success/Failure; cảnh báo lịch thi; giữ Room cache và lần login đầu đã sửa |\n| Sửa login / HTTP 401 / refresh token | `schools/tnut/TnutApiClient.kt`, `TnutSchoolProvider.kt`, `data/AuthStore.kt`, `ui/LoginScreen.kt` | `tests/test_auth_refresh.py`; 401 thật và logical 401, token rotate, offline |
| API trả lịch sai/thi thiếu/trùng/trang tiếp theo | `schools/tnut/TnutApi.kt`, `TnutRepository.kt`, `rust-core/src/portal.rs`, `rust/RustPortalCore.kt` | Rust unit tests, `RustPortalModelsTest.kt`, model/request tests, cache lỗi |
| Tối ưu JSON/JNI/CPU khi load lịch | `rust-core/src/portal.rs`, `rust-core/src/android.rs`, `rust/RustPortalCore.kt` | Kiểm tra định dạng `TKB1`, 4 ABI, APK/JNI và so sánh output trước/sau |
| Sửa lưu ghi chú, offline, DB migration | `data/AppDatabase.kt`, `schools/tnut/TnutRepository.kt` | `tests/test_data_safety.py` và dữ liệu giả lập upgrade v1→v4 |
| Thay logic ngày, giờ thi, hiển thị tuần | `rust-core/src/lib.rs`, `rust/RustScheduleCore.kt`, `ui/WeekDatePicker.kt` | `RustScheduleCoreTest.kt`, `WeekDatePickerTest.kt`, `cargo test` |
| Đổi theme/card/Glass hoặc màn hình cài đặt | `ui/`, `ui/glass/`, `ui/theme/`, `res/` | Compose tương ứng; UI không làm thay cache/API |
| Thông báo nhắc hôm nay/ngày mai | `notifications/ScheduleReminders.kt`, `data/AuthStore.kt`, `ui/SettingsScreen.kt` | `ScheduleRemindersTest.kt`, permission, reboot/timezone |
| Widget không cập nhật/chuyển ngày sai | `widget/TimetableWidgetProvider.kt`, `res/layout/`, `res/xml/` | Test widget trên máy, không phụ thuộc màn Compose |
| Cập nhật APK lỗi, sai chữ ký hoặc download | `update/`, `.github/workflows/build.yml`, `app/build.gradle.kts` | Kiểm tra `update.json`, checksum, APK signing/upgrade |
| Hỗ trợ trường mới | `schools/SchoolProvider.kt`, `SchoolRegistry.kt`, `docs/ADDING_SCHOOL.md` | Phải tách cache/token theo trường + tài khoản, migration, test offline |
| Build không có `libtkb_core.so` | `scripts/build-rust-android.sh`, `app/build.gradle.kts`, `rust-core/src/android.rs` | 4 ABI, `verifyRustNativeLibraries`, R8/JNI |

## 3. Luồng dữ liệu và ranh giới thay đổi

### A. Khởi động, đọc cache, vẽ màn hình

`MainActivity.onCreate` → `AuthStore` + `AppDatabase.get` → `SchoolRegistry.forId(...).open(...)` → `SchoolSession.repository` → `TimetableViewModel` → `cachedTimetable` (snapshot đầu) + Room `Flow` → `TimetableScreen`.

- Đọc đồng thời tuần và lessons từ **cùng transaction** để tránh khung hình đầu tiên bị rỗng/không nhất quán.
- `TimetableScreen.kt` là file lớn, chứa cả ViewModel và Compose. Tìm symbol cụ thể thay vì sửa toàn file.
- Khi sửa scroll/chọn ngày phải đồng bộ `selectedWeek`, ngày đang chọn, vị trí list và `WeekDatePicker`; xử lý kéo qua đầu/cuối tuần, khi chưa có lịch và khi tháng đổi.
- Không bắt mạng mới chỉ để dựng màn hình từ cache.

### B. Login và refresh

`LoginViewModel` → `SchoolSession.login` (`TnutSchoolProvider`) → `TnutApi` / `TnutApiClient` → `AuthStore`. Request sau dùng interceptor và authenticator; có xử lý **HTTP 401 thật** lẫn **HTTP 200 chứa logical 401**. Refresh có khóa để tránh đồng thời refresh sai, có đường fallback login bằng credentials đang lưu. Giữ nguyên logic hạn chế retry và không làm mất token cũ khi response thiếu refresh token mới.

### C. Đồng bộ lịch học/lịch thi

`TimetableViewModel.refresh` → `SchoolRepository.refresh` → `TnutRepository.refresh`:
- Tải **bytes gốc** của API lịch học (bộ lọc học kỳ; chưa xác minh server hỗ trợ từ ngày) → `RustPortalCore.mapTimetable` nhận `fromYmd` theo giờ Việt Nam → `rust-core/src/portal.rs` parse/validate dữ liệu nguồn, lọc tuần có ngày kết thúc từ hôm nay và buổi học có ngày từ hôm nay → encode nhị phân `TKB1` → Kotlin decode thành `PeriodEntity/WeekEntity/LessonEntity` → `TkbDao.replaceTimetable` transaction. Không thay đổi format nhị phân; chỉ thay số bản ghi và chữ ký JNI.
- Đồng bộ lịch thi được kích hoạt độc lập/concurrently; từng loại lịch thi (giữa kỳ/cuối kỳ) tải phân trang; Rust parse, validate, loại trùng → `TkbDao.replaceExamsByType`. Một endpoint lỗi không được xóa lịch loại còn lại.
- Giao dịch thay dữ liệu **chỉ bắt đầu sau khi** toàn bộ dữ liệu cần thiết đã hợp lệ. Khi lịch trống/lỗi/401 không ghi đè cache tốt bằng danh sách rỗng.
- Ghi chú `lesson_notes` không thuộc transaction thay timetable. Đừng xóa ghi chú khi cập nhật lessons.
- Đồng bộ thành công kích hoạt cập nhật widget; reminder đọc Room thay vì polling portal liên tục.

### D. JNI và định dạng nhị phân

- `rust-core/src/android.rs` phải export tên JNI khớp 100% với `RustPortalCore.kt` và `RustScheduleCore.kt`.
- `RustPortalCore.kt` giải mã nhị phân `TKB1` từ Rust (`portal.rs`), **không** tự ý khôi phục tuyến Kotlin DTO → JSON → JNI → JSON → Kotlin.
- Nếu thay cấu trúc record, trường, thứ tự hoặc version của `TKB1`: cập nhật encoder Rust, decoder Kotlin, test fixture và kiểm tra thực tế trên Android.
- `RustScheduleCore.kt` có fallback cho host JVM tests; Android runtime phải có thư viện native thực, không im lặng fallback trên thiết bị.
- Kiểm tra symbol JNI và R8 keep rules trong `app/proguard-rules.pro` nếu rename class/method.

## 4. Kiểm tra trước khi commit

Từ root repository:

```bash
cargo clippy --manifest-path rust-core/Cargo.toml --all-targets -- -D warnings

# Cần Android SDK/NDK + cargo-ndk, biên dịch native trước khi build APK:
bash scripts/build-rust-android.sh
./gradlew assembleDebug
```

- Khi sửa Rust / binary `TKB1` / ABI: chạy `cargo clippy`, Android build và kiểm tra native trên thiết bị/emulator nếu có.
- Khi sửa Release/update: đối chiếu APK signing, checksum và update metadata; không thay khóa ký.
- `build.yml` chạy trên **mọi push lên main**, kể cả docs-only; không coi một commit tài liệu là bản release đã xác minh.

## 5. Format kết quả bắt buộc khi AI sửa code

Khi kết thúc một thay đổi, báo cáo ngắn gọn:

1. **Mục tiêu** và điểm gây lỗi (nếu tìm được, phân biệt với giả thuyết).
2. **File thay đổi** và lý do, kèm ảnh hưởng Kotlin ↔ Rust ↔ Room.
3. **Bất biến đã giữ**: data/offline, token, JNI/ABI, giao diện (nếu liên quan).
4. **Test/build đã thực sự chạy** cùng kết quả; nếu chưa chạy phải nói rõ.
5. **Commit/branch/Actions** nếu đã thực hiện; tuyệt đối không bịa URL, trạng thái pass, số run.

Không tạo branch/PR, chạy release, xóa workflow, đổi application ID, đổi DB schema, thay cấu trúc UI hoặc khởi tạo project mới **nếu yêu cầu hiện tại không đòi hỏi**. Không tự ý đưa thêm thư viện, dịch vụ trung gian hay dữ liệu mẫu giả vào Room.

## 6. Tài liệu đi kèm

- [README.md](README.md): giới thiệu, thư mục, lệnh build, đường tải APK.
- [rust-core/README.md](rust-core/README.md): phạm vi Rust migration, giao thức gọi native và giới hạn.
- [docs/ADDING_SCHOOL.md](docs/ADDING_SCHOOL.md): cách thêm trường; vấn đề cách ly cache và migration.
