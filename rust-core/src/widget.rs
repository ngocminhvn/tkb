//! Widget sizing rules in dp and sp.
#[derive(Clone, Copy)]
#[repr(i32)]
pub enum Layout {
    Square,
    Medium,
    Wide,
}

impl TryFrom<i32> for Layout {
    type Error = ();

    fn try_from(value: i32) -> Result<Self, Self::Error> {
        match value {
            0 => Ok(Self::Square),
            1 => Ok(Self::Medium),
            2 => Ok(Self::Wide),
            _ => Err(()),
        }
    }
}

pub fn layout(width: i32, min_height: i32) -> Layout {
    if width <= 220 || width <= min_height.saturating_add(32) {
        Layout::Square
    } else if width < 320 {
        Layout::Medium
    } else {
        Layout::Wide
    }
}

pub fn density(count: i32, height: i32, layout: Layout) -> [i32; 4] {
    if count <= 0 {
        return [12, 11, 8, 4];
    }
    let (header, base_subject, normal_padding, compact_padding, compact_count) = match layout {
        Layout::Square => (48, 12, 8, 5, 4),
        Layout::Medium => (52, 12, 10, 7, 4),
        Layout::Wide => (52, 14, 12, 8, 5),
    };
    let per_lesson = height.saturating_sub(header).max(40) / count;
    let count_reduction = match count {
        ..=2 => 0,
        3 => 1,
        4 => 2,
        _ => 4,
    };
    let height_reduction = match per_lesson {
        48.. => 0,
        36..=47 => 1,
        28..=35 => 2,
        _ => 4,
    };
    let subject = (base_subject - count_reduction.max(height_reduction)).max(7);
    let vertical = match per_lesson {
        52.. if count <= 2 => 6,
        38.. => 4,
        28..=37 => 2,
        _ => 0,
    };
    let horizontal = if count >= compact_count {
        compact_padding
    } else {
        normal_padding
    };
    [subject, (subject - 1).max(7), horizontal, vertical]
}

use crate::database::Database;

pub struct WidgetItem {
    pub title: String,
    pub time: String,
    pub is_exam: bool,
}

pub struct WidgetSchedule {
    pub title: String,
    pub offset: i32,
    pub items: Vec<WidgetItem>,
}

pub fn build_widget_schedule(
    db_path: &str,
    today_ymd: &str,
    stored_offset: Option<i32>,
    is_square: bool,
    now_minutes: i32,
) -> Result<WidgetSchedule, String> {
    let db = Database::open(db_path).map_err(|e| format!("Lỗi mở tkb.db: {e}"))?;
    let periods = db.periods().map_err(|e| format!("Lỗi đọc periods: {e}"))?;

    let (offset, target_date, lessons, exams) = if let Some(stored) = stored_offset {
        let off = stored.clamp(-365, 365);
        let date = if off == 0 {
            today_ymd.to_string()
        } else {
            crate::add_calendar_days(today_ymd, off).unwrap_or_else(|| today_ymd.to_string())
        };
        let l = db.lessons_on(&date).map_err(|e| format!("Lỗi đọc lessons: {e}"))?;
        let e = db.exams_on(&date).map_err(|e| format!("Lỗi đọc exams: {e}"))?;
        (off, date, l, e)
    } else {
        let today_lessons = db.lessons_on(today_ymd).map_err(|e| format!("Lỗi đọc lessons: {e}"))?;
        let today_exams = db.exams_on(today_ymd).map_err(|e| format!("Lỗi đọc exams: {e}"))?;

        let lesson_ends: Vec<String> = today_lessons
            .iter()
            .map(|l| {
                let last_period = l.tiet_bat_dau + l.so_tiet - 1;
                periods
                    .iter()
                    .find(|p| p.tiet == last_period)
                    .map(|p| p.gio_ket_thuc.clone())
                    .unwrap_or_default()
            })
            .collect();
        let lesson_end_refs: Vec<&str> = lesson_ends.iter().map(String::as_str).collect();
        let exam_pairs: Vec<(&str, i32)> = today_exams
            .iter()
            .map(|e| (e.gio_bat_dau.as_str(), e.so_phut))
            .collect();

        let auto_offset = if crate::widget_day_finished(now_minutes, lesson_end_refs, exam_pairs) {
            1
        } else {
            0
        };

        if auto_offset == 0 {
            (0, today_ymd.to_string(), today_lessons, today_exams)
        } else {
            let next_date = crate::add_calendar_days(today_ymd, 1).unwrap_or_else(|| today_ymd.to_string());
            let l = db.lessons_on(&next_date).map_err(|e| format!("Lỗi đọc lessons: {e}"))?;
            let e = db.exams_on(&next_date).map_err(|e| format!("Lỗi đọc exams: {e}"))?;
            (1, next_date, l, e)
        }
    };

    let date_dm = crate::format_dm(&target_date);
    let title = match offset {
        -1 => {
            if is_square {
                "Hôm qua".to_string()
            } else {
                format!("Hôm qua • {date_dm}")
            }
        }
        0 => {
            if is_square {
                "Hôm nay".to_string()
            } else {
                format!("Hôm nay • {date_dm}")
            }
        }
        1 => {
            if is_square {
                "Ngày mai".to_string()
            } else {
                format!("Ngày mai • {date_dm}")
            }
        }
        _ => date_dm,
    };

    let mut items = Vec::with_capacity(exams.len() + lessons.len());
    for exam in exams {
        let end_time = crate::exam_end_time(&exam.gio_bat_dau, exam.so_phut).unwrap_or_default();
        let time_str = if !end_time.is_empty() {
            format!("{}–{}", exam.gio_bat_dau, end_time)
        } else if !exam.gio_bat_dau.is_empty() {
            exam.gio_bat_dau
        } else {
            "Thi".to_string()
        };
        items.push(WidgetItem {
            title: exam.ten_mon,
            time: time_str,
            is_exam: true,
        });
    }

    for lesson in lessons {
        let last_period = lesson.tiet_bat_dau + lesson.so_tiet - 1;
        let start = periods
            .iter()
            .find(|p| p.tiet == lesson.tiet_bat_dau)
            .map(|p| p.gio_bat_dau.as_str());
        let end = periods
            .iter()
            .find(|p| p.tiet == last_period)
            .map(|p| p.gio_ket_thuc.as_str())
            .unwrap_or_default();
        let time_str = crate::format_lesson_time(lesson.tiet_bat_dau, start, end, true);
        items.push(WidgetItem {
            title: lesson.ten_mon,
            time: time_str,
            is_exam: false,
        });
    }

    Ok(WidgetSchedule {
        title,
        offset,
        items,
    })
}

pub fn encode_widget_schedule_wire(schedule: &WidgetSchedule) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(b"TKB1W");
    out.extend_from_slice(&schedule.offset.to_be_bytes());

    let title_bytes = schedule.title.as_bytes();
    out.extend_from_slice(&(title_bytes.len() as u16).to_be_bytes());
    out.extend_from_slice(title_bytes);

    out.extend_from_slice(&(schedule.items.len() as u32).to_be_bytes());
    for item in &schedule.items {
        out.push(if item.is_exam { 1 } else { 0 });

        let t_bytes = item.title.as_bytes();
        out.extend_from_slice(&(t_bytes.len() as u16).to_be_bytes());
        out.extend_from_slice(t_bytes);

        let time_bytes = item.time.as_bytes();
        out.extend_from_slice(&(time_bytes.len() as u16).to_be_bytes());
        out.extend_from_slice(time_bytes);
    }
    out
}
