//! Pure TNUT payload validation and mapping. No credentials or network traffic.
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Deserialize)]
struct Timetable {
    #[serde(rename = "ds_tiet_trong_ngay", default)]
    periods: Vec<Period>,
    #[serde(rename = "ds_tuan_tkb", default)]
    weeks: Vec<Week>,
}
#[derive(Deserialize)]
struct Period {
    tiet: i32,
    #[serde(rename = "gio_bat_dau")]
    start: String,
    #[serde(rename = "gio_ket_thuc")]
    end: String,
}
#[derive(Deserialize)]
struct Week {
    #[serde(rename = "tuan_hoc_ky")]
    number: i32,
    #[serde(rename = "ngay_bat_dau", default)]
    start: String,
    #[serde(rename = "ngay_ket_thuc", default)]
    end: String,
    #[serde(rename = "thong_tin_tuan", default)]
    title: String,
    #[serde(rename = "ds_thoi_khoa_bieu", default)]
    lessons: Vec<Lesson>,
}
#[derive(Deserialize)]
struct Lesson {
    #[serde(rename = "id_tkb")]
    id: String,
    #[serde(rename = "thu_kieu_so")]
    weekday: i32,
    #[serde(rename = "tiet_bat_dau")]
    first_period: i32,
    #[serde(rename = "so_tiet")]
    period_count: i32,
    #[serde(rename = "ma_mon", default)]
    course_code: String,
    #[serde(rename = "ten_mon", default)]
    title: String,
    #[serde(rename = "so_tin_chi", default)]
    credits: String,
    #[serde(rename = "ma_phong", default)]
    room: String,
    #[serde(rename = "ten_giang_vien", default)]
    teacher: String,
    #[serde(rename = "ten_lop", default)]
    class_name: String,
    #[serde(rename = "ma_nhom", default)]
    group: String,
    #[serde(rename = "ngay_hoc", default)]
    date: String,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct PeriodRow {
    tiet: i32,
    gio_bat_dau: String,
    gio_ket_thuc: String,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct WeekRow {
    hoc_ky: i32,
    tuan_hoc_ky: i32,
    ngay_bat_dau: String,
    ngay_ket_thuc: String,
    thong_tin_tuan: String,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct LessonRow {
    id_tkb: String,
    hoc_ky: i32,
    tuan_hoc_ky: i32,
    ngay_hoc: String,
    thu_kieu_so: i32,
    tiet_bat_dau: i32,
    so_tiet: i32,
    ma_mon: String,
    ten_mon: String,
    so_tin_chi: String,
    ma_phong: String,
    ten_giang_vien: String,
    ten_lop: String,
    ma_nhom: String,
}
#[derive(Serialize)]
struct TimetableRows {
    periods: Vec<PeriodRow>,
    weeks: Vec<WeekRow>,
    lessons: Vec<LessonRow>,
}

/// Return entity-shaped JSON after verifying a complete semester timetable.
/// Reject empty data before a transaction can replace the offline cache.
pub fn map_timetable(semester: i32, payload: &str) -> Result<String, String> {
    let source: Timetable = serde_json::from_str(payload)
        .map_err(|error| format!("Invalid TNUT timetable: {error}"))?;
    if source.weeks.is_empty() || source.weeks.iter().all(|week| week.lessons.is_empty()) {
        return Err(format!("Portal trả lịch học rỗng (học kỳ {semester}). Dữ liệu đã lưu được giữ nguyên."));
    }
    let periods = source.periods.into_iter().map(|period| PeriodRow {
        tiet: period.tiet, gio_bat_dau: period.start, gio_ket_thuc: period.end,
    }).collect();
    let mut weeks = Vec::new();
    let mut lessons = Vec::new();
    for week in source.weeks {
        weeks.push(WeekRow {
            hoc_ky: semester, tuan_hoc_ky: week.number, ngay_bat_dau: week.start,
            ngay_ket_thuc: week.end, thong_tin_tuan: week.title,
        });
        for lesson in week.lessons {
            lessons.push(LessonRow {
                id_tkb: lesson.id, hoc_ky: semester, tuan_hoc_ky: week.number,
                ngay_hoc: lesson.date, thu_kieu_so: lesson.weekday,
                tiet_bat_dau: lesson.first_period, so_tiet: lesson.period_count,
                ma_mon: lesson.course_code, ten_mon: lesson.title,
                so_tin_chi: lesson.credits, ma_phong: lesson.room,
                ten_giang_vien: lesson.teacher, ten_lop: lesson.class_name,
                ma_nhom: lesson.group,
            });
        }
    }
    serde_json::to_string(&TimetableRows { periods, weeks, lessons })
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
/// Decode one exam page; failure does not erase previously cached exams.
pub fn map_exam_page(semester: i32, is_midterm: bool, payload: &str) -> Result<String, String> {
    let page: ExamPage = serde_json::from_str(payload)
        .map_err(|error| format!("Invalid TNUT exam page: {error}"))?;
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
    serde_json::to_string(&ExamPageRows { rows, total_pages: page.total_pages, row_count })
        .map_err(|error| format!("Cannot encode exam rows: {error}"))
}

#[cfg(test)]
mod tests {
    use super::*;
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
    fn refuses_missing_required_exam_data() {
        let input = r#"{"ds_lich_thi":[{"ngay_thi":"07/11/2026"}]}"#;
        assert!(map_exam_page(20261, false, input).is_err());
    }
}
