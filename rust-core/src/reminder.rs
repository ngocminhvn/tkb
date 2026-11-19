//! Schedule reminder assembly and formatting.
use crate::database::Database;

pub struct ReminderNotification {
    pub title: String,
    pub summary: String,
    pub message: String,
}

pub fn build_reminder_notification(
    db_path: &str,
    semester: i32,
    is_tomorrow: bool,
    today_ymd: &str,
) -> Result<ReminderNotification, String> {
    let target = if is_tomorrow {
        crate::add_calendar_days(today_ymd, 1).unwrap_or_else(|| today_ymd.to_string())
    } else {
        today_ymd.to_string()
    };
    let display_date = crate::format_dmy(&target);
    let title = if is_tomorrow {
        format!("Lịch ngày mai • {display_date}")
    } else {
        format!("Lịch hôm nay • {display_date}")
    };

    let db = Database::open(db_path).map_err(|e| format!("Lỗi mở tkb.db: {e}"))?;
    let lessons = db.reminder_lessons_on(semester, &target).map_err(|e| format!("Lỗi đọc lessons: {e}"))?;
    let exams = db.reminder_exams_on(semester, &target).map_err(|e| format!("Lỗi đọc exams: {e}"))?;
    let periods = db.periods().map_err(|e| format!("Lỗi đọc periods: {e}"))?;

    let mut contents: Vec<(String, String)> = Vec::with_capacity(lessons.len() + exams.len());
    for lesson in lessons {
        let hour = periods
            .iter()
            .find(|p| p.tiet == lesson.tiet_bat_dau)
            .map(|p| p.gio_bat_dau.clone())
            .unwrap_or_else(|| format!("Tiết {}", lesson.tiet_bat_dau));
        contents.push((hour, format!("{} • {}", lesson.ten_mon, lesson.ma_phong)));
    }
    for exam in exams {
        contents.push((exam.gio_bat_dau, format!("THI: {} • {}", exam.ten_mon, exam.ma_phong)));
    }

    let (summary, message) = crate::reminder_message(contents);

    Ok(ReminderNotification {
        title,
        summary,
        message,
    })
}

pub fn encode_reminder_wire(rem: &ReminderNotification) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(b"TKB1R");

    let t = rem.title.as_bytes();
    out.extend_from_slice(&(t.len() as u16).to_be_bytes());
    out.extend_from_slice(t);

    let s = rem.summary.as_bytes();
    out.extend_from_slice(&(s.len() as u16).to_be_bytes());
    out.extend_from_slice(s);

    let m = rem.message.as_bytes();
    out.extend_from_slice(&(m.len() as u16).to_be_bytes());
    out.extend_from_slice(m);

    out
}
