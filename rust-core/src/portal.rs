//! Pure TNUT payload validation and mapping. No credentials or network traffic.
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::borrow::Cow;

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
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct PeriodRow<'a> {
    tiet: i32,
    gio_bat_dau: Cow<'a, str>,
    gio_ket_thuc: Cow<'a, str>,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct WeekRow<'a> {
    hoc_ky: i32,
    tuan_hoc_ky: i32,
    ngay_bat_dau: Cow<'a, str>,
    ngay_ket_thuc: Cow<'a, str>,
    thong_tin_tuan: Cow<'a, str>,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
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
#[derive(Serialize)]
struct TimetableRows<'a> {
    periods: Vec<PeriodRow<'a>>,
    weeks: Vec<WeekRow<'a>>,
    lessons: Vec<LessonRow<'a>>,
}

/// Return entity-shaped JSON after verifying a complete semester timetable.
/// Reject empty data before a transaction can replace the offline cache.
fn timetable_rows<'a>(semester: i32, source: Timetable<'a>) -> Result<TimetableRows<'a>, String> {
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

/// Public JSON mapper kept for Rust regression fixtures; Android uses binary.
pub fn map_timetable(semester: i32, payload: &str) -> Result<String, String> {
    let source: Timetable<'_> = serde_json::from_str(payload)
        .map_err(|error| format!("Invalid TNUT timetable: {error}"))?;
    serde_json::to_string(&timetable_rows(semester, source)?)
        .map_err(|error| format!("Cannot encode timetable rows: {error}"))
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
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
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
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
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

/// Follow TNUT page metadata if provided, otherwise use the 100-item page size.
pub fn exam_has_next(page: usize, total_pages: Option<usize>, rows: usize) -> bool {
    match total_pages.filter(|total| *total > 0) {
        Some(total) => page < total,
        None => rows >= 100,
    }
}

/// Drop duplicate exam records across multiple pages without changing any
/// Room-backed columns; retain the first occurrence in original page order.
pub fn dedupe_exam_rows(payload: &str) -> Result<String, String> {
    use std::collections::HashSet;
    let rows: Vec<Value> = serde_json::from_str(payload)
        .map_err(|error| format!("Invalid exam rows for deduplication: {error}"))?;
    let mut seen = HashSet::new();
    let mut unique = Vec::with_capacity(rows.len());
    for row in rows {
        let get = |field: &str| -> String {
            row.get(field).and_then(Value::as_str).unwrap_or_default().to_string()
        };
        let key = (
            row.get("isHoanThi").and_then(Value::as_bool).unwrap_or(false),
            get("maMon"), get("ngayThi"), get("gioBatDau"),
            get("soBaoDanh"), get("maPhong"),
        );
        if seen.insert(key) {
            unique.push(row);
        }
    }
    serde_json::to_string(&unique).map_err(|error| format!("Cannot encode unique exams: {error}"))
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

/// Public JSON mapper kept for Rust regression fixtures; Android uses binary.
pub fn map_exam_page(semester: i32, is_midterm: bool, payload: &str) -> Result<String, String> {
    let page: ExamPage = serde_json::from_str(payload)
        .map_err(|error| format!("Invalid TNUT exam page: {error}"))?;
    serde_json::to_string(&exam_rows(semester, is_midterm, page)?)
        .map_err(|error| format!("Cannot encode exam rows: {error}"))
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
fn binary_i32(out: &mut Vec<u8>, value: i32) {
    out.extend_from_slice(&value.to_be_bytes());
}
fn binary_i64(out: &mut Vec<u8>, value: i64) {
    out.extend_from_slice(&value.to_be_bytes());
}
fn binary_count(out: &mut Vec<u8>, value: usize) -> Result<(), String> {
    let count = i32::try_from(value).map_err(|_| "Too many TNUT rows".to_string())?;
    binary_i32(out, count);
    Ok(())
}
fn binary_str(out: &mut Vec<u8>, value: &str) -> Result<(), String> {
    binary_count(out, value.len())?;
    out.extend_from_slice(value.as_bytes());
    Ok(())
}
fn binary_header(kind: u8) -> Vec<u8> {
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
pub fn map_timetable_wire(semester: i32, bytes: &[u8]) -> Result<Vec<u8>, String> {
    let data: Timetable<'_> = parse_envelope(bytes, "thời khóa biểu")?;
    pack_timetable(timetable_rows(semester, data)?)
}
pub fn map_exam_wire(semester: i32, is_midterm: bool, bytes: &[u8]) -> Result<Vec<u8>, String> {
    let data: ExamPage = parse_envelope(bytes, "lịch thi")?;
    pack_exams(exam_rows(semester, is_midterm, data)?)
}

/// Fast native inspection of a peeked HTTP 200 portal body to detect logical 401
/// or `"notallowed"` responses without allocating regexes on the JVM.
pub fn is_logical_unauthorized(bytes: &[u8]) -> bool {
    let Ok(text) = std::str::from_utf8(bytes) else {
        return false;
    };
    if text.as_bytes().windows(10).any(|w| w.eq_ignore_ascii_case(b"notallowed")) {
        return true;
    }
    // Match `"code"\s*:\s*401\b`
    let mut rest = text;
    while let Some(idx) = rest.find("\"code\"") {
        rest = &rest[idx + 6..];
        let trimmed = rest.trim_start();
        if let Some(after_colon) = trimmed.strip_prefix(':') {
            let after_ws = after_colon.trim_start();
            if let Some(after_401) = after_ws.strip_prefix("401") {
                let next_byte = after_401.as_bytes().first().copied();
                if !matches!(next_byte, Some(b'0'..=b'9')) {
                    return true;
                }
            }
        }
    }
    false
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

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn detects_logical_unauthorized_payloads() {
        assert!(is_logical_unauthorized(br#"{"result":"false","code":401,"message":"expired"}"#));
        assert!(is_logical_unauthorized(br#"{"code" : 401}"#));
        assert!(is_logical_unauthorized(br#"{"error":"NotAllowed"}"#));
        assert!(!is_logical_unauthorized(br#"{"code":200,"data":{}}"#));
        assert!(!is_logical_unauthorized(br#"{"code":4010}"#));
    }
    #[test]
    fn verifies_update_manifest_and_packs_wire() {
        let sha = "a".repeat(64);
        let json = format!(
            r#"{{"versionCode":15,"versionName":"2.1","apkUrl":"https://ngocminhvn.github.io/tkb/TKB-SV.apk","apkSha256":"{sha}","apkSize":1048576,"notes":"Fast Rust"}}"#
        );
        let packed = verify_update_manifest(14, "https://ngocminhvn.github.io/tkb/", json.as_bytes())
            .unwrap()
            .expect("should have update");
        assert_eq!(&packed[..5], b"TKB1U");
        let none = verify_update_manifest(15, "https://ngocminhvn.github.io/tkb/", json.as_bytes()).unwrap();
        assert!(none.is_none());
    }
    #[test]
    fn binary_wire_reads_portal_envelope_without_json_roundtrip() {
        let timetable = r#"{"code":200,"data":{"ds_tiet_trong_ngay":[],"ds_tuan_tkb":[{"tuan_hoc_ky":2,"ds_thoi_khoa_bieu":[{"id_tkb":"abc","thu_kieu_so":3,"tiet_bat_dau":1,"so_tiet":2,"ten_mon":"Toán"}]}]}}"#.as_bytes();
        let packed = map_timetable_wire(20261, timetable).unwrap();
        assert_eq!(&packed[..5], b"TKB1T");
        assert!(packed.windows("Toán".len()).any(|v| v == "Toán".as_bytes()));
        let exam = br#"{"code":200,"data":{"total_pages":1,"ds_lich_thi":[{"ma_mon":"MAT","ngay_thi":"7/11/2026"}]}}"#;
        let packed = map_exam_wire(20261, false, exam).unwrap();
        assert_eq!(&packed[..5], b"TKB1E");
        assert!(packed.windows(3).any(|v| v == b"MAT"));
        assert!(map_timetable_wire(20261, br#"{"code":200,"data":{"ds_tuan_tkb":[]}}"#).is_err());
        assert!(map_timetable_wire(20261, br#"{"code":401,"message":"expired"}"#).is_err());
        assert!(map_exam_wire(20261, false, br#"{"code":200,"data":{"ds_lich_thi":[{}]}}"#).is_err());
    }
    #[test]
    fn rejects_empty_timetable_to_preserve_offline_cache() {
        assert!(map_timetable(20261, r#"{"ds_tuan_tkb":[]}"#).is_err());
        assert!(map_timetable(20261, r#"{"ds_tuan_tkb":[{"tuan_hoc_ky":1,"ds_thoi_khoa_bieu":[]}]}"#).is_err());
    }
    #[test]
    fn maps_timetable_into_room_columns() {
        let input = r#"{"ds_tiet_trong_ngay":[{"tiet":1,"gio_bat_dau":"07:15","gio_ket_thuc":"08:05"}],"ds_tuan_tkb":[{"tuan_hoc_ky":2,"ngay_bat_dau":"02/11/2026","ngay_ket_thuc":"08/11/2026","ds_thoi_khoa_bieu":[{"id_tkb":"abc","thu_kieu_so":2,"tiet_bat_dau":1,"so_tiet":2,"ten_mon":"Toán"}]}]}"#;
        let result: Value = serde_json::from_str(&map_timetable(20261, input).unwrap()).unwrap();
        assert_eq!(result["periods"][0]["gioBatDau"], "07:15");
        assert_eq!(result["weeks"][0]["hocKy"], 20261);
        assert_eq!(result["lessons"][0]["idTkb"], "abc");
        assert_eq!(result["lessons"][0]["tenMon"], "Toán");
    }
    #[test]
    fn maps_exam_and_preserves_midterm_and_postponed_flags() {
        let input = r#"{"total_pages":1,"ds_lich_thi":[{"ma_mon":"MEC01","ngay_thi":"07/11/2026","so_phut":"90"}],"ds_lich_hoan_thi":[{"ma_mon":"ABC","ngay_thi":"08/11/2026"}]}"#;
        let result: Value = serde_json::from_str(&map_exam_page(20261, true, input).unwrap()).unwrap();
        assert_eq!(result["totalPages"], 1);
        assert_eq!(result["rowCount"], 2);
        assert_eq!(result["rows"][0]["soPhut"], 90);
        assert_eq!(result["rows"][0]["isGiuaKy"], true);
        assert_eq!(result["rows"][1]["isHoanThi"], true);
    }
    #[test]
    fn keeps_exam_page_order_and_unique_records() {
        let sample = r#"[{"maMon":"A","ngayThi":"2026-11-07","gioBatDau":"07:00","soBaoDanh":"1","maPhong":"101"},{"maMon":"A","ngayThi":"2026-11-07","gioBatDau":"07:00","soBaoDanh":"1","maPhong":"101"},{"maMon":"B","ngayThi":"2026-11-08"}]"#;
        let rows: Vec<Value> = serde_json::from_str(&dedupe_exam_rows(sample).unwrap()).unwrap();
        assert_eq!(rows.len(), 2);
        assert_eq!(rows[0]["maMon"], "A");
        assert_eq!(rows[1]["maMon"], "B");
        assert!(exam_has_next(1, Some(2), 10));
        assert!(!exam_has_next(2, Some(2), 100));
        assert!(exam_has_next(1, None, 100));
        assert!(!exam_has_next(1, None, 99));
    }
    #[test]
    fn preserves_trim_and_login_name_rules() {
        assert_eq!(normalize_note("  Mang laptop  "), "Mang laptop");
        assert_eq!(normalize_username("  SV123@tnut.edu.vn "), "SV123");
    }
    #[test]
    fn refuses_missing_required_exam_data() {
        let input = r#"{"ds_lich_thi":[{"ngay_thi":"07/11/2026"}]}"#;
        assert!(map_exam_page(20261, false, input).is_err());
    }
    #[test]
    fn timetable_wire_handles_escaped_strings() {
        let payload = r#"{"code":200,"data":{"ds_tuan_tkb":[{"tuan_hoc_ky":1,"ds_thoi_khoa_bieu":[{"id_tkb":"a","thu_kieu_so":2,"tiet_bat_dau":1,"so_tiet":1,"ten_mon":"Gi\u1ea3i \"t\u00edch\""}]}]}}"#;
        let packed = map_timetable_wire(1, payload.as_bytes()).unwrap();
        let needle = "Giải \"tích\"".as_bytes();
        assert!(packed.windows(needle.len()).any(|v| v == needle));
    }
}
