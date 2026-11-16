//! Pure TNUT payload validation and mapping. No credentials or network traffic.
use serde::Deserialize;
use serde_json::Value;
use std::borrow::Cow;
use chrono::NaiveDate;

#[derive(Deserialize)]
struct Timetable<'a> {
    #[serde(rename = "ds_tiet_trong_ngay", default, borrow)]
    periods: Vec<Period<'a>>,
    #[serde(rename = "ds_tuan_tkb", default, borrow)]
    weeks: Vec<Week<'a>>,
}
#[derive(Deserialize)]
struct Period<'a> {
    tiet: i32,
    #[serde(rename = "gio_bat_dau", borrow)]
    start: Cow<'a, str>,
    #[serde(rename = "gio_ket_thuc", borrow)]
    end: Cow<'a, str>,
}
#[derive(Deserialize)]
struct Week<'a> {
    #[serde(rename = "tuan_hoc_ky")]
    number: i32,
    #[serde(rename = "ngay_bat_dau", default, borrow)]
    start: Cow<'a, str>,
    #[serde(rename = "ngay_ket_thuc", default, borrow)]
    end: Cow<'a, str>,
    #[serde(rename = "thong_tin_tuan", default, borrow)]
    title: Cow<'a, str>,
    #[serde(rename = "ds_thoi_khoa_bieu", default, borrow)]
    lessons: Vec<Lesson<'a>>,
}
#[derive(Deserialize)]
struct Lesson<'a> {
    #[serde(rename = "id_tkb", borrow)]
    id: Cow<'a, str>,
    #[serde(rename = "thu_kieu_so")]
    weekday: i32,
    #[serde(rename = "tiet_bat_dau")]
    first_period: i32,
    #[serde(rename = "so_tiet")]
    period_count: i32,
    #[serde(rename = "ma_mon", default, borrow)]
    course_code: Cow<'a, str>,
    #[serde(rename = "ten_mon", default, borrow)]
    title: Cow<'a, str>,
    #[serde(rename = "so_tin_chi", default, borrow)]
    credits: Cow<'a, str>,
    #[serde(rename = "ma_phong", default, borrow)]
    room: Cow<'a, str>,
    #[serde(rename = "ten_giang_vien", default, borrow)]
    teacher: Cow<'a, str>,
    #[serde(rename = "ten_lop", default, borrow)]
    class_name: Cow<'a, str>,
    #[serde(rename = "ma_nhom", default, borrow)]
    group: Cow<'a, str>,
    #[serde(rename = "ngay_hoc", default, borrow)]
    date: Cow<'a, str>,
}
struct PeriodRow<'a> {
    tiet: i32,
    gio_bat_dau: Cow<'a, str>,
    gio_ket_thuc: Cow<'a, str>,
}
struct WeekRow<'a> {
    hoc_ky: i32,
    tuan_hoc_ky: i32,
    ngay_bat_dau: Cow<'a, str>,
    ngay_ket_thuc: Cow<'a, str>,
    thong_tin_tuan: Cow<'a, str>,
}
struct LessonRow<'a> {
    id_tkb: Cow<'a, str>,
    hoc_ky: i32,
    tuan_hoc_ky: i32,
    ngay_hoc: Cow<'a, str>,
    thu_kieu_so: i32,
    tiet_bat_dau: i32,
    so_tiet: i32,
    ma_mon: Cow<'a, str>,
    ten_mon: Cow<'a, str>,
    so_tin_chi: Cow<'a, str>,
    ma_phong: Cow<'a, str>,
    ten_giang_vien: Cow<'a, str>,
    ten_lop: Cow<'a, str>,
    ma_nhom: Cow<'a, str>,
}
struct TimetableRows<'a> {
    periods: Vec<PeriodRow<'a>>,
    weeks: Vec<WeekRow<'a>>,
    lessons: Vec<LessonRow<'a>>,
}

/// Return entity-shaped JSON after verifying a complete semester timetable.
/// Reject empty data before a transaction can replace the offline cache.
fn timetable_rows<'a>(
    semester: i32,
    source: Timetable<'a>,
    from_ymd: &str,
) -> Result<TimetableRows<'a>, String> {
    let today = NaiveDate::parse_from_str(from_ymd, "%Y-%m-%d")
        .map_err(|_| "Invalid Vietnam calendar start date".to_string())?;
    if source.weeks.is_empty() || source.weeks.iter().all(|week| week.lessons.is_empty()) {
        return Err(format!("Portal trả lịch học rỗng (học kỳ {semester}). Dữ liệu đã lưu được giữ nguyên."));
    }
    let periods = source.periods.into_iter().map(|period| PeriodRow {
        tiet: period.tiet, gio_bat_dau: period.start, gio_ket_thuc: period.end,
    }).collect();
    let total_lessons: usize = source.weeks.iter().map(|w| w.lessons.len()).sum();
    let mut weeks = Vec::with_capacity(source.weeks.len());
    let mut lessons = Vec::with_capacity(total_lessons);
    for week in source.weeks {
        let start_date = if week.start.is_empty() {
            week.start
        } else {
            Cow::Owned(crate::normalize_date(&week.start))
        };
        let end_date = if week.end.is_empty() {
            week.end
        } else {
            Cow::Owned(crate::normalize_date(&week.end))
        };
        // A portal week may have a stale/incorrect end date. Never discard
        // future lesson rows just because this metadata ends before today.
        let parsed_end = NaiveDate::parse_from_str(&end_date, "%Y-%m-%d").ok();
        let has_future_lesson = week.lessons.iter().any(|lesson| {
            let normalized = crate::normalize_date(&lesson.date);
            NaiveDate::parse_from_str(&normalized, "%Y-%m-%d")
                .map(|date| date >= today)
                .unwrap_or(true)
        });
        if parsed_end.is_some_and(|date| date < today) && !has_future_lesson {
            continue;
        }
        weeks.push(WeekRow {
            hoc_ky: semester, tuan_hoc_ky: week.number, ngay_bat_dau: start_date,
            ngay_ket_thuc: end_date, thong_tin_tuan: week.title,
        });
        for lesson in week.lessons {
            let lesson_date = if lesson.date.is_empty() {
                lesson.date
            } else {
                Cow::Owned(crate::normalize_date(&lesson.date))
            };
            // In the current week, omit classes before today's date while
            // still keeping the week's boundaries for known-empty days.
            if NaiveDate::parse_from_str(&lesson_date, "%Y-%m-%d")
                .is_ok_and(|date| date < today)
            {
                continue;
            }
            lessons.push(LessonRow {
                id_tkb: lesson.id, hoc_ky: semester, tuan_hoc_ky: week.number,
                ngay_hoc: lesson_date, thu_kieu_so: lesson.weekday,
                tiet_bat_dau: lesson.first_period, so_tiet: lesson.period_count,
                ma_mon: lesson.course_code, ten_mon: lesson.title,
                so_tin_chi: lesson.credits, ma_phong: lesson.room,
                ten_giang_vien: lesson.teacher, ten_lop: lesson.class_name,
                ma_nhom: lesson.group,
            });
        }
    }
    Ok(TimetableRows { periods, weeks, lessons })
}

#[derive(Deserialize)]
struct ExamPage {
    #[serde(rename = "total_pages")]
    total_pages: Option<usize>,
    #[serde(rename = "ds_lich_thi", default)]
    regular: Option<Vec<Exam>>,
    #[serde(rename = "ds_lich_hoan_thi", default)]
    postponed: Option<Vec<Exam>>,
}
#[derive(Deserialize)]
struct Exam {
    #[serde(rename = "so_thu_tu")]
    ordinal: Option<Value>,
    #[serde(rename = "ma_mon")]
    course_code: Option<String>,
    #[serde(rename = "ten_mon")]
    title: Option<String>,
    #[serde(rename = "ten_mon_eg")]
    alt_title: Option<String>,
    #[serde(rename = "ngay_thi")]
    date: Option<String>,
    #[serde(rename = "gio_bat_dau")]
    start: Option<String>,
    #[serde(rename = "so_phut")]
    minutes: Option<Value>,
    #[serde(rename = "ma_phong")]
    room: Option<String>,
    #[serde(rename = "dia_diem_thi")]
    venue: Option<String>,
    #[serde(rename = "hinh_thuc_thi")]
    format: Option<String>,
    #[serde(rename = "ghi_chu_htt")]
    format_note: Option<String>,
    #[serde(rename = "so_bao_danh")]
    candidate: Option<String>,
    #[serde(rename = "to_thi")]
    exam_group: Option<String>,
    #[serde(rename = "dot_thi")]
    session: Option<String>,
    #[serde(rename = "ghi_chu_sv")]
    student_note: Option<String>,
}
struct ExamRow {
    id: i64,
    hoc_ky: i32,
    is_giua_ky: bool,
    so_thu_tu: i32,
    ma_mon: String,
    ten_mon: String,
    ngay_thi: String,
    gio_bat_dau: String,
    so_phut: i32,
    ma_phong: String,
    dia_diem_thi: String,
    hinh_thuc_thi: String,
    so_bao_danh: String,
    dot_thi: String,
    ghi_chu_sv: String,
    is_hoan_thi: bool,
}
struct ExamPageRows {
    rows: Vec<ExamRow>,
    total_pages: Option<usize>,
    row_count: usize,
}
fn optional_int(value: Option<Value>) -> i32 {
    match value {
        Some(Value::Number(number)) => number.as_i64().and_then(|n| i32::try_from(n).ok()).unwrap_or(0),
        Some(Value::String(value)) => value.parse().unwrap_or(0),
        _ => 0,
    }
}
fn trimmed(value: Option<String>) -> String {
    value.unwrap_or_default().trim().to_string()
}
fn valid_portal_date(value: &str) -> bool {
    value.len() == 10 && value.bytes().enumerate().all(|(index, byte)| {
        if index == 4 || index == 7 { byte == b'-' } else { byte.is_ascii_digit() }
    })
}

/// Normalize editable notes without touching the existing SQLite schema.
pub fn normalize_note(input: &str) -> String {
    input.trim().to_string()
}

/// Student portal login accepts the ID prefix even when a user types @domain.
pub fn normalize_username(input: &str) -> String {
    input.trim().split('@').next().unwrap_or("").to_string()
}

pub const EXAM_PAGE_SIZE: usize = 100;
pub(crate) const MAX_EXAM_PAGES: usize = 50;

pub fn timetable_request(semester: i32) -> Vec<u8> {
    serde_json::json!({"filter": {"hoc_ky": semester}}).to_string().into_bytes()
}

pub fn exam_request(semester: i32, is_midterm: bool, page: usize) -> Vec<u8> {
    serde_json::json!({
        "filter": {"hoc_ky": semester, "is_giua_ky": is_midterm},
        "additional": {
            "paging": {"limit": EXAM_PAGE_SIZE, "page": page},
            "ordering": [{"name": null, "order_type": null}]
        }
    })
    .to_string()
    .into_bytes()
}

pub fn next_exam_page(
    page: usize,
    total_pages: Option<usize>,
    rows: usize,
) -> Result<Option<usize>, String> {
    if !exam_has_next(page, total_pages, rows) {
        return Ok(None);
    }
    if page >= MAX_EXAM_PAGES {
        return Err(format!("Lịch thi vượt quá {MAX_EXAM_PAGES} trang; giữ dữ liệu cũ."));
    }
    Ok(Some(page + 1))
}

/// Follow TNUT page metadata if provided, otherwise use the 100-item page size.
pub fn exam_has_next(page: usize, total_pages: Option<usize>, rows: usize) -> bool {
    match total_pages.filter(|total| *total > 0) {
        Some(total) => page < total,
        None => rows >= EXAM_PAGE_SIZE,
    }
}

/// Keep the first row for each identity, preserving the order of fetched pages.
pub fn first_exam_indices<T: Eq + std::hash::Hash>(
    keys: impl IntoIterator<Item = T>,
) -> Vec<i32> {
    let mut seen = std::collections::HashSet::new();
    keys.into_iter()
        .zip(0..)
        .filter_map(|(key, index)| seen.insert(key).then_some(index))
        .collect()
}

/// Decode one exam page; failure does not erase previously cached exams.
fn exam_rows(semester: i32, is_midterm: bool, page: ExamPage) -> Result<ExamPageRows, String> {
    let regular = page.regular.unwrap_or_default();
    let postponed = page.postponed.unwrap_or_default();
    let row_count = regular.len() + postponed.len();
    let mut rows = Vec::with_capacity(row_count);
    for (exams, is_postponed) in [(regular, false), (postponed, true)] {
        for exam in exams {
            let date = crate::normalize_date(exam.date.as_deref().unwrap_or(""));
            let code = trimmed(exam.course_code);
            if !is_postponed && (code.is_empty() || !valid_portal_date(&date)) {
                return Err("Portal trả buổi thi thiếu mã môn/ngày thi; giữ lịch đã lưu.".into());
            }
            rows.push(ExamRow {
                id: 0, hoc_ky: semester, is_giua_ky: is_midterm,
                so_thu_tu: optional_int(exam.ordinal), ma_mon: code,
                ten_mon: trimmed(exam.title.or(exam.alt_title)),
                ngay_thi: date, gio_bat_dau: trimmed(exam.start),
                so_phut: optional_int(exam.minutes), ma_phong: trimmed(exam.room),
                dia_diem_thi: trimmed(exam.venue),
                hinh_thuc_thi: trimmed(exam.format.or(exam.format_note)),
                so_bao_danh: trimmed(exam.candidate.or(exam.exam_group)),
                dot_thi: trimmed(exam.session), ghi_chu_sv: trimmed(exam.student_note),
                is_hoan_thi: is_postponed,
            });
        }
    }
    Ok(ExamPageRows { rows, total_pages: page.total_pages, row_count })
}


const MAX_WIRE_PAYLOAD: usize = 8 * 1024 * 1024;

/// The Android boundary carries original portal bytes into Rust and a single
/// length-prefixed binary buffer back. No Kotlin model->JSON->Rust->JSON bounce.
#[derive(Deserialize)]
struct PortalEnvelope<T> {
    code: i32,
    message: Option<String>,
    data: Option<T>,
}
fn parse_envelope<'a, T: Deserialize<'a>>(
    input: &'a [u8],
    section: &str,
) -> Result<T, String> {
    if input.len() > MAX_WIRE_PAYLOAD {
        return Err(format!("TNUT {section} payload exceeds the 8 MiB limit"));
    }
    let wrapper: PortalEnvelope<T> = serde_json::from_slice(input)
        .map_err(|error| format!("Invalid TNUT {section} response: {error}"))?;
    if wrapper.code != 200 {
        return Err(wrapper.message.unwrap_or_else(|| {
            format!("Không tải được {section} ({})", wrapper.code)
        }));
    }
    wrapper.data.ok_or_else(|| format!("TNUT {section} response has no data"))
}
pub(crate) fn binary_i32(out: &mut Vec<u8>, value: i32) {
    out.extend_from_slice(&value.to_be_bytes());
}
fn binary_i64(out: &mut Vec<u8>, value: i64) {
    out.extend_from_slice(&value.to_be_bytes());
}
pub(crate) fn binary_count(out: &mut Vec<u8>, value: usize) -> Result<(), String> {
    let count = i32::try_from(value).map_err(|_| "Too many TNUT rows".to_string())?;
    binary_i32(out, count);
    Ok(())
}
pub(crate) fn binary_str(out: &mut Vec<u8>, value: &str) -> Result<(), String> {
    binary_count(out, value.len())?;
    out.extend_from_slice(value.as_bytes());
    Ok(())
}
pub(crate) fn binary_header(kind: u8) -> Vec<u8> {
    let mut out = Vec::with_capacity(4096);
    out.extend_from_slice(b"TKB1");
    out.push(kind);
    out
}
fn pack_timetable(rows: TimetableRows<'_>) -> Result<Vec<u8>, String> {
    let mut out = binary_header(b'T');
    binary_count(&mut out, rows.periods.len())?;
    for row in rows.periods {
        binary_i32(&mut out, row.tiet);
        binary_str(&mut out, &row.gio_bat_dau)?;
        binary_str(&mut out, &row.gio_ket_thuc)?;
    }
    binary_count(&mut out, rows.weeks.len())?;
    for row in rows.weeks {
        binary_i32(&mut out, row.hoc_ky);
        binary_i32(&mut out, row.tuan_hoc_ky);
        binary_str(&mut out, &row.ngay_bat_dau)?;
        binary_str(&mut out, &row.ngay_ket_thuc)?;
        binary_str(&mut out, &row.thong_tin_tuan)?;
    }
    binary_count(&mut out, rows.lessons.len())?;
    for row in rows.lessons {
        binary_str(&mut out, &row.id_tkb)?;
        binary_i32(&mut out, row.hoc_ky);
        binary_i32(&mut out, row.tuan_hoc_ky);
        binary_str(&mut out, &row.ngay_hoc)?;
        binary_i32(&mut out, row.thu_kieu_so);
        binary_i32(&mut out, row.tiet_bat_dau);
        binary_i32(&mut out, row.so_tiet);
        binary_str(&mut out, &row.ma_mon)?;
        binary_str(&mut out, &row.ten_mon)?;
        binary_str(&mut out, &row.so_tin_chi)?;
        binary_str(&mut out, &row.ma_phong)?;
        binary_str(&mut out, &row.ten_giang_vien)?;
        binary_str(&mut out, &row.ten_lop)?;
        binary_str(&mut out, &row.ma_nhom)?;
    }
    Ok(out)
}
fn pack_exams(page: ExamPageRows) -> Result<Vec<u8>, String> {
    let mut out = binary_header(b'E');
    binary_i32(&mut out, page.total_pages.and_then(|n| i32::try_from(n).ok()).unwrap_or(-1));
    binary_count(&mut out, page.row_count)?;
    binary_count(&mut out, page.rows.len())?;
    for row in page.rows {
        binary_i64(&mut out, row.id);
        binary_i32(&mut out, row.hoc_ky);
        out.push(u8::from(row.is_giua_ky));
        binary_i32(&mut out, row.so_thu_tu);
        binary_str(&mut out, &row.ma_mon)?;
        binary_str(&mut out, &row.ten_mon)?;
        binary_str(&mut out, &row.ngay_thi)?;
        binary_str(&mut out, &row.gio_bat_dau)?;
        binary_i32(&mut out, row.so_phut);
        binary_str(&mut out, &row.ma_phong)?;
        binary_str(&mut out, &row.dia_diem_thi)?;
        binary_str(&mut out, &row.hinh_thuc_thi)?;
        binary_str(&mut out, &row.so_bao_danh)?;
        binary_str(&mut out, &row.dot_thi)?;
        binary_str(&mut out, &row.ghi_chu_sv)?;
        out.push(u8::from(row.is_hoan_thi));
    }
    Ok(out)
}

/// Receives exactly the network JSON bytes; does not reserialize Kotlin DTOs.
pub fn map_timetable_wire(
    semester: i32,
    from_ymd: &str,
    bytes: &[u8],
) -> Result<Vec<u8>, String> {
    let data: Timetable<'_> = parse_envelope(bytes, "thời khóa biểu")?;
    pack_timetable(timetable_rows(semester, data, from_ymd)?)
}
pub fn map_exam_wire(semester: i32, is_midterm: bool, bytes: &[u8]) -> Result<Vec<u8>, String> {
    let data: ExamPage = parse_envelope(bytes, "lịch thi")?;
    pack_exams(exam_rows(semester, is_midterm, data)?)
}

fn portal_string<'de, D: serde::Deserializer<'de>>(input: D) -> Result<Option<String>, D::Error> {
    match Value::deserialize(input)? {
        Value::Null => Ok(None),
        Value::String(value) => Ok(Some(value)),
        Value::Number(value) => Ok(Some(value.to_string())),
        Value::Bool(value) => Ok(Some(value.to_string())),
        _ => Err(serde::de::Error::custom("expected a scalar portal field")),
    }
}

#[derive(Deserialize)]
struct LoginResponse {
    #[serde(default, deserialize_with = "portal_string")]
    access_token: Option<String>,
    #[serde(default, deserialize_with = "portal_string")]
    refresh_token: Option<String>,
    #[serde(default, deserialize_with = "portal_string")]
    idpc: Option<String>,
}

pub fn map_login_wire(bytes: &[u8]) -> Result<Vec<u8>, String> {
    if bytes.len() > 128 * 1024 {
        return Err("Phản hồi đăng nhập TNUT quá lớn.".into());
    }
    let response: LoginResponse = serde_json::from_slice(bytes)
        .map_err(|_| "Phản hồi đăng nhập TNUT không hợp lệ.".to_string())?;
    let mut out = binary_header(b'L');
    binary_str(&mut out, response.access_token.as_deref().unwrap_or_default())?;
    for field in [response.refresh_token, response.idpc] {
        out.push(u8::from(field.is_some()));
        if let Some(value) = field {
            binary_str(&mut out, &value)?;
        }
    }
    Ok(out)
}

#[derive(Deserialize)]
struct AvatarResponse {
    data: Option<AvatarData>,
}

#[derive(Deserialize)]
struct AvatarData {
    thong_tin_sinh_vien: Option<StudentImage>,
}

#[derive(Deserialize)]
struct StudentImage {
    #[serde(default, deserialize_with = "portal_string")]
    ho_ten: Option<String>,
    #[serde(default, deserialize_with = "portal_string")]
    ten_sinh_vien: Option<String>,
    #[serde(default, deserialize_with = "portal_string")]
    ten_sv: Option<String>,
    #[serde(default, deserialize_with = "portal_string")]
    image: Option<String>,
}

pub fn map_avatar_wire(bytes: &[u8]) -> Result<Vec<u8>, String> {
    if bytes.len() > MAX_WIRE_PAYLOAD {
        return Err("TNUT avatar payload exceeds the 8 MiB limit".into());
    }
    let response: AvatarResponse = serde_json::from_slice(bytes)
        .map_err(|error| format!("Invalid TNUT avatar response: {error}"))?;
    let mut name = String::new();
    let mut image = None;
    if let Some(profile) = response.data.and_then(|data| data.thong_tin_sinh_vien) {
        name = [profile.ho_ten, profile.ten_sinh_vien, profile.ten_sv]
            .into_iter()
            .flatten()
            .find(|name| !name.trim().is_empty())
            .map(|name| name.trim().to_owned())
            .unwrap_or_default();
        image = profile.image.filter(|image| !image.trim().is_empty());
    }
    let mut out = binary_header(b'A');
    binary_str(&mut out, &name)?;
    out.push(u8::from(image.is_some()));
    if let Some(image) = image {
        let base64 = image.split_once(',').map_or(image.as_str(), |(_, body)| body);
        binary_str(&mut out, base64)?;
    }
    Ok(out)
}

/// Fast native inspection of a peeked HTTP 200 portal body to detect logical 401
/// or `"notallowed"` responses without allocating regexes on the JVM.
pub fn is_logical_unauthorized(bytes: &[u8]) -> bool {
    if bytes.windows(b"notallowed".len()).any(|window| {
        window.eq_ignore_ascii_case(b"notallowed")
    }) {
        return true;
    }
    // Same case-insensitive code:401 word-boundary recognition as the old JVM
    // regex; avoid extra allocations and a second UTF-8 decoding on Android.
    const CODE_KEY: &[u8] = b"\"code\"";
    for (index, window) in bytes.windows(CODE_KEY.len()).enumerate() {
        if !window.eq_ignore_ascii_case(CODE_KEY) {
            continue;
        }
        let remaining = bytes[index + CODE_KEY.len()..].trim_ascii_start();
        let Some(after_colon) = remaining.strip_prefix(b":") else {
            continue;
        };
        if let Some(after_401) = after_colon.trim_ascii_start().strip_prefix(b"401") {
            let word_char_follows = after_401.first().is_some_and(|byte| {
                byte.is_ascii_alphanumeric() || *byte == b'_'
            });
            if !word_char_follows {
                return true;
            }
        }
    }
    false
}

#[cfg(test)]
mod auth_response_tests {
    use super::is_logical_unauthorized;
    #[test]
    fn detects_logical_http_200_unauthorized() {
        assert!(is_logical_unauthorized(br#"{ "code": 401 }"#));
        assert!(is_logical_unauthorized(br#"{ "CODE": 401, "message": "expired" }"#));
        assert!(is_logical_unauthorized(br#"{ "message": "NotAllowed" }"#));
        assert!(!is_logical_unauthorized(br#"{ "code": 4010 }"#));
        assert!(!is_logical_unauthorized(br#"{ "code": 401abc }"#));
        assert!(!is_logical_unauthorized(br#"{ "code": 200 }"#));
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct UpdateManifestPayload {
    version_code: i32,
    #[serde(default)]
    version_name: String,
    #[serde(default)]
    apk_url: String,
    #[serde(default)]
    apk_sha256: String,
    #[serde(default)]
    apk_size: i64,
    #[serde(default)]
    compressed_apk_url: String,
    #[serde(default)]
    compressed_apk_sha256: String,
    #[serde(default)]
    compressed_apk_size: i64,
    #[serde(default)]
    notes: String,
    #[serde(default)]
    published_at: String,
}

fn valid_sha256_hex(value: &str) -> bool {
    let trimmed = value.trim();
    trimmed.len() == 64 && trimmed.bytes().all(|b| b.is_ascii_hexdigit())
}

/// Parse and verify an `update.json` manifest in Rust. Returns `Ok(None)` when
/// the remote version is not newer than `current_version_code` or fails safety checks.
pub fn verify_update_manifest(
    current_version_code: i32,
    allowed_prefix: &str,
    payload: &[u8],
) -> Result<Option<Vec<u8>>, String> {
    if payload.len() > 256 * 1024 {
        return Err("Update manifest exceeds 256 KiB".into());
    }
    let item: UpdateManifestPayload = serde_json::from_slice(payload)
        .map_err(|e| format!("Invalid update manifest: {e}"))?;
    if item.version_code <= current_version_code {
        return Ok(None);
    }
    let apk_url = item.apk_url.trim();
    let apk_sha = item.apk_sha256.trim().to_ascii_lowercase();
    if !apk_url.starts_with(allowed_prefix) || !valid_sha256_hex(&apk_sha) {
        return Ok(None);
    }
    let comp_url = item.compressed_apk_url.trim();
    let comp_sha = item.compressed_apk_sha256.trim().to_ascii_lowercase();
    let has_valid_compressed = comp_url.is_empty()
        || (comp_url.starts_with(allowed_prefix)
            && comp_url.ends_with(".xz")
            && valid_sha256_hex(&comp_sha));
    let (final_comp_url, final_comp_sha, final_comp_size) = if has_valid_compressed {
        (comp_url, comp_sha.as_str(), item.compressed_apk_size.max(0))
    } else {
        ("", "", 0)
    };
    let version_name = {
        let v = item.version_name.trim();
        if v.is_empty() {
            item.version_code.to_string()
        } else {
            v.to_string()
        }
    };
    let mut out = binary_header(b'U');
    binary_i32(&mut out, item.version_code);
    binary_str(&mut out, &version_name)?;
    binary_str(&mut out, apk_url)?;
    binary_str(&mut out, &apk_sha)?;
    binary_i64(&mut out, item.apk_size.max(0));
    binary_str(&mut out, final_comp_url)?;
    binary_str(&mut out, final_comp_sha)?;
    binary_i64(&mut out, final_comp_size);
    binary_str(&mut out, item.notes.trim())?;
    binary_str(&mut out, item.published_at.trim())?;
    Ok(Some(out))
}


/// Read the released GitHub Pages format, including older uncompressed releases.
pub fn read_release_update(
    current_version: i64,
    allowed_prefix: &str,
    input: &[u8],
) -> Result<Vec<u8>, String> {
    if input.len() > 256 * 1024 {
        return Err("Update manifest exceeds 256 KiB".into());
    }
    let json: serde_json::Map<String, Value> = serde_json::from_slice(input)
        .map_err(|err| format!("Invalid update manifest: {err}"))?;
    let string = |key| match json.get(key) {
        Some(Value::String(value)) => value.trim().to_owned(),
        Some(value) => value.to_string(),
        None => String::new(),
    };
    let number = |key| match json.get(key) {
        Some(Value::Number(value)) => value.as_i64().or_else(|| value.as_f64().map(|n| n as i64)),
        Some(Value::String(value)) => value.trim().parse::<f64>().ok().map(|n| n as i64),
        _ => None,
    }
    .filter(|&n| n > 0);
    let fallback = |preferred: String, legacy: String| {
        if preferred.is_empty() { legacy } else { preferred }
    };
    let version = number("versionCode").ok_or("update.json thiếu versionCode hợp lệ.")?;
    let name = string("versionName");
    if name.is_empty() {
        return Err("update.json thiếu versionName.".into());
    }
    let apk_url = string("apkUrl");
    let package_url = fallback(string("packageUrl"), apk_url.clone());
    if !package_url.starts_with(allowed_prefix) {
        return Err("Nguồn cập nhật TKB SV không hợp lệ.".into());
    }
    let compression = fallback(string("compression").to_ascii_lowercase(), "none".into());
    if !matches!(compression.as_str(), "none" | "xz") {
        return Err("Kiểu nén cập nhật không được hỗ trợ.".into());
    }
    let checksum = fallback(string("packageSha256"), string("sha256")).to_ascii_lowercase();
    if !valid_sha256_hex(&checksum) {
        return Err("update.json thiếu SHA-256 hợp lệ.".into());
    }
    if version <= current_version {
        return Ok(Vec::new());
    }

    let size = number("packageSizeBytes").or_else(|| number("sizeBytes"));
    let notes = fallback(string("changelog"), "Bản cập nhật TKB SV mới.".into());
    let mut out = binary_header(b'P');
    binary_i64(&mut out, version);
    for field in [&name, &apk_url, &package_url, &compression, &checksum] {
        binary_str(&mut out, field)?;
    }
    binary_i64(&mut out, size.unwrap_or(0));
    binary_str(&mut out, &notes)?;
    Ok(out)
}

#[cfg(test)]
mod migration_tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn preserves_timetable_and_exam_request_payloads() {
        let timetable: Value = serde_json::from_slice(&timetable_request(20261)).unwrap();
        let exam: Value = serde_json::from_slice(&exam_request(20261, false, 3)).unwrap();
        assert_eq!(timetable, json!({"filter": {"hoc_ky": 20261}}));
        assert_eq!(exam, json!({
            "filter": {"hoc_ky": 20261, "is_giua_ky": false},
            "additional": {
                "paging": {"limit": 100, "page": 3},
                "ordering": [{"name": null, "order_type": null}]
            }
        }));
    }

    #[test]
    fn preserves_exam_page_limit_and_first_occurrence_order() {
        assert_eq!(next_exam_page(1, Some(3), 1), Ok(Some(2)));
        assert_eq!(next_exam_page(50, Some(50), 100), Ok(None));
        assert!(next_exam_page(50, None, 100).is_err());
        assert_eq!(next_exam_page(2, None, 99), Ok(None));
        assert_eq!(first_exam_indices([("A", false), ("B", false), ("A", false), ("A", true)]),
            vec![0, 1, 3]);
    }

    #[test]
    fn packs_login_tokens_with_nullable_rotation_fields() {
        let login = br#"{"access_token":"new","refresh_token":null,"idpc":1}"#;
        assert_eq!(map_login_wire(login).unwrap(), b"TKB1L\0\0\0\x03new\0\x01\0\0\0\x011");
        let malformed = br#"{"access_token":["token"]}"#;
        assert_eq!(map_login_wire(malformed).unwrap_err(), "Phản hồi đăng nhập TNUT không hợp lệ.");
    }

    #[test]
    fn picks_first_nonblank_portal_name_and_extracts_data_uri() {
        let response = br#"{"data":{"thong_tin_sinh_vien":{
            "ho_ten":" ","ten_sinh_vien":" An ","ten_sv":"Other","image":"data:image/png;base64,YQ=="
        }}}"#;
        assert_eq!(map_avatar_wire(response).unwrap(), b"TKB1A\0\0\0\x02An\x01\0\0\0\x04YQ==");
        assert_eq!(map_avatar_wire(b"{}").unwrap(), b"TKB1A\0\0\0\0\0");
    }

    #[test]
    fn checks_current_and_legacy_release_metadata() {
        let prefix = "https://example.test/tkb/";
        let plain = format!("{prefix}TKB-SV.apk");
        let compressed = format!("{plain}.xz");
        let apk_hash = "a".repeat(64);
        let package_hash = "B".repeat(64);
        let mut item = json!({
            "versionCode": 3_000_000_001_i64, "versionName": "1.0.1",
            "apkUrl": plain, "sha256": apk_hash, "sizeBytes": 120,
            "packageUrl": compressed, "packageSha256": package_hash, "packageSizeBytes": 100,
            "compression": "XZ", "changelog": "New version",
        });
        let wire = read_release_update(0, prefix, &serde_json::to_vec(&item).unwrap()).unwrap();
        assert!(wire.starts_with(b"TKB1P"));
        assert_eq!(i64::from_be_bytes(wire[5..13].try_into().unwrap()), 3_000_000_001);
        assert!(wire.windows(64).any(|bytes| bytes == package_hash.to_ascii_lowercase().as_bytes()));
        assert!(read_release_update(3_000_000_001, prefix, &serde_json::to_vec(&item).unwrap())
            .unwrap().is_empty());

        let object = item.as_object_mut().unwrap();
        for key in ["packageUrl", "packageSha256", "packageSizeBytes", "compression"] {
            object.remove(key);
        }
        let wire = read_release_update(0, prefix, &serde_json::to_vec(&item).unwrap()).unwrap();
        assert!(wire.windows(64).any(|bytes| bytes == apk_hash.as_bytes()));
        item["packageUrl"] = json!("https://example.test/other/file.apk");
        assert!(read_release_update(0, prefix, &serde_json::to_vec(&item).unwrap()).is_err());
    }
}

pub fn exam_sync_issue(semester: i32, updated: bool, errors: &[String]) -> Option<String> {
    if !errors.is_empty() {
        return Some(format!(
            "Đồng bộ lịch thi chưa hoàn tất: {}. Dữ liệu hợp lệ đã tải được lưu, dữ liệu còn lại được giữ nguyên.",
            errors.join("; ")
        ));
    }
    (!updated).then(|| format!(
        "Portal trả danh sách thi giữa kỳ và cuối kỳ rỗng cho học kỳ {semester}. Không có dữ liệu mới để đồng bộ; lịch đã lưu được giữ nguyên."
    ))
}

#[cfg(test)]
mod exam_sync_tests {
    use super::exam_sync_issue;

    #[test]
    fn distinguishes_success_empty_and_partial_failure() {
        assert!(exam_sync_issue(20261, true, &[]).is_none());
        assert!(exam_sync_issue(20261, false, &[]).unwrap().contains("20261"));
        let errors = ["timeout".into(), "mất mạng".into()];
        for updated in [false, true] {
            assert!(exam_sync_issue(20261, updated, &errors).unwrap().contains("timeout; mất mạng"));
        }
    }
}
