//! Pure TNUT grade transcript parsing, GPA calculations and TKB1G binary packing.
use serde_json::Value;
use std::borrow::Cow;
use std::collections::HashMap;
use crate::portal::{binary_count, binary_header, binary_i32, binary_i64, binary_str};

fn default_symbols() -> HashMap<String, String> {
    let mut m = HashMap::new();
    let defs = [
        ("CC", "Chuyên cần"),
        ("KT", "Kiểm tra"),
        ("GK", "Giữa kỳ"),
        ("TH", "Thực hành"),
        ("BT", "Bài tập"),
        ("K1", "Kiểm tra"),
        ("T1", "Điểm thi"),
        ("T2", "Thi lại"),
    ];
    for (k, v) in defs {
        m.insert(k.to_string(), v.to_string());
    }
    m
}

fn format_hoc_ky_name(code: &str) -> String {
    let trimmed = code.trim();
    if trimmed.len() == 5 && trimmed.chars().all(|c| c.is_ascii_digit()) {
        if let Ok(year) = trimmed[0..4].parse::<i32>() {
            let term = &trimmed[4..5];
            return format!("Học kỳ {term} ({year}-{})", year + 1);
        }
    }
    format!("Học kỳ {trimmed}")
}

fn classify_gpa(gpa4_str: &str) -> &'static str {
    let Ok(gpa) = gpa4_str.trim().parse::<f64>() else { return ""; };
    if gpa >= 3.6 {
        "Xuất sắc"
    } else if gpa >= 3.2 {
        "Giỏi"
    } else if gpa >= 2.5 {
        "Khá"
    } else if gpa >= 2.0 {
        "Trung bình"
    } else {
        "Yếu"
    }
}

fn determine_is_dat(ket_qua: &str, diem_chu: &str, diem_tk: &str) -> bool {
    let kq_lower = ket_qua.to_lowercase();
    if kq_lower.contains("không") {
        return false;
    }
    if kq_lower.contains("đạt") {
        return true;
    }
    let diem_chu_upper = diem_chu.trim().to_uppercase();
    if diem_chu_upper == "F" {
        return false;
    }
    if matches!(
        diem_chu_upper.as_str(),
        "A" | "A+" | "B" | "B+" | "C" | "C+" | "D" | "D+" | "M"
    ) {
        return true;
    }
    if let Ok(num) = diem_tk.trim().parse::<f64>() {
        return num >= 4.0;
    }
    true
}

/// Safely extract trimmed string from any JSON value (string, number, bool, null).
fn val_to_str<'a>(val: Option<&'a Value>) -> Cow<'a, str> {
    match val {
        Some(Value::String(s)) => Cow::Borrowed(s.trim()),
        Some(Value::Number(n)) => Cow::Owned(n.to_string()),
        Some(Value::Bool(b)) => Cow::Borrowed(if *b { "true" } else { "false" }),
        _ => Cow::Borrowed(""),
    }
}

/// Parse symbols JSON into a key-value mapping with full resilience against null/numeric fields.
pub fn parse_symbols(symbols_payload: &[u8]) -> HashMap<String, String> {
    let mut map = default_symbols();
    if symbols_payload.is_empty() {
        return map;
    }
    if let Ok(root) = serde_json::from_slice::<Value>(symbols_payload) {
        let data = root.get("data").unwrap_or(&root);
        if let Some(items) = data.get("ds_ky_hieu_diem").and_then(|v| v.as_array()) {
            for item in items {
                let k = val_to_str(item.get("ky_hieu")).trim().to_string();
                let v = val_to_str(item.get("ten")).trim().to_string();
                if !k.is_empty() && !v.is_empty() {
                    map.insert(k, v);
                }
            }
        }
    }
    map
}

/// Parse raw TNUT grade payload and encode into compact TKB1G binary wire format.
/// Resilient against null fields, numeric floats/ints in place of strings, and missing data.
pub fn parse_and_pack_grades(
    student_id: &str,
    student_name: &str,
    grades_payload: &[u8],
    symbols_payload: &[u8],
    updated_at: i64,
) -> Result<Vec<u8>, String> {
    let symbols = parse_symbols(symbols_payload);

    let root: Value = serde_json::from_slice(grades_payload)
        .map_err(|e| format!("Invalid TNUT grades response: {e}"))?;

    let code = match root.get("code") {
        Some(Value::Number(n)) => n.as_i64().unwrap_or(200),
        Some(Value::String(s)) => s.parse::<i64>().unwrap_or(200),
        _ => 200,
    };

    if code != 0 && code != 200 {
        let msg = root.get("message")
            .and_then(|m| m.as_str())
            .unwrap_or("Không tải được bảng điểm");
        return Err(format!("{msg} ({code})"));
    }

    let data = root.get("data").unwrap_or(&root);
    let empty_vec = Vec::new();
    let semesters = data.get("ds_diem_hocky")
        .and_then(|v| v.as_array())
        .unwrap_or(&empty_vec);

    struct ProcessedComponent {
        ky_hieu: String,
        ten_thanh_phan: String,
        diem: String,
    }

    struct ProcessedCourse {
        ma_mon: String,
        ten_mon: String,
        nhom_to: String,
        so_tin_chi: String,
        diem_thi: String,
        diem_giua_ky: String,
        diem_tk: String,
        diem_tk_so: String,
        diem_chu: String,
        ket_qua: String,
        is_dat: bool,
        components: Vec<ProcessedComponent>,
    }

    struct ProcessedSemester {
        hoc_ky: String,
        ten_hoc_ky: String,
        dtb_hk_10: String,
        dtb_hk_4: String,
        so_tin_chi_hk: String,
        diem_rl_hk: String,
        phan_loai_rl_hk: String,
        dtb_tich_luy_10: String,
        dtb_tich_luy_4: String,
        so_tin_chi_tich_luy: String,
        xep_loai_tkb_hk: String,
        diem_rl_tich_luy: String,
        courses: Vec<ProcessedCourse>,
    }

    let mut total_courses = 0usize;
    let mut processed_semesters = Vec::with_capacity(semesters.len());

    for sem in semesters {
        let hoc_ky = val_to_str(sem.get("hoc_ky")).trim().to_string();
        let mut ten_hoc_ky = val_to_str(sem.get("ten_hoc_ky")).trim().to_string();
        if ten_hoc_ky.is_empty() && !hoc_ky.is_empty() {
            ten_hoc_ky = format_hoc_ky_name(&hoc_ky);
        }

        let courses_arr = sem.get("ds_diem_mon_hoc")
            .and_then(|v| v.as_array())
            .unwrap_or(&empty_vec);

        let mut processed_courses = Vec::with_capacity(courses_arr.len());
        for course in courses_arr {
            let ma_mon = val_to_str(course.get("ma_mon")).trim().to_string();
            let ten_mon = val_to_str(course.get("ten_mon")).trim().to_string();
            let nhom_to = val_to_str(course.get("nhom_to")).trim().to_string();
            let so_tin_chi = val_to_str(course.get("so_tin_chi")).trim().to_string();
            let diem_thi = val_to_str(course.get("diem_thi")).trim().to_string();
            let diem_giua_ky = val_to_str(course.get("diem_giua_ky")).trim().to_string();
            let diem_tk = val_to_str(course.get("diem_tk")).trim().to_string();
            let diem_tk_so = val_to_str(course.get("diem_tk_so")).trim().to_string();
            let diem_chu = val_to_str(course.get("diem_tk_chu")).trim().to_uppercase();
            let mut ket_qua = val_to_str(course.get("ket_qua")).trim().to_string();

            let is_dat = determine_is_dat(&ket_qua, &diem_chu, &diem_tk);
            if ket_qua.is_empty() {
                ket_qua = if is_dat { "Đạt".to_string() } else { "Không đạt".to_string() };
            }

            let comp_arr = course.get("ds_diem_thanh_phan")
                .and_then(|v| v.as_array())
                .unwrap_or(&empty_vec);

            let mut processed_components = Vec::with_capacity(comp_arr.len());
            for comp in comp_arr {
                let ky_hieu = val_to_str(comp.get("ky_hieu")).trim().to_string();
                let diem_tp = val_to_str(comp.get("diem_thanh_phan")).trim().to_string();
                if ky_hieu.is_empty() && diem_tp.is_empty() {
                    continue;
                }
                let ten_tp = symbols.get(&ky_hieu).cloned().unwrap_or_else(|| {
                    let raw_name = val_to_str(comp.get("ten_thanh_phan")).trim().to_string();
                    if raw_name.is_empty() { ky_hieu.clone() } else { raw_name }
                });
                processed_components.push(ProcessedComponent {
                    ky_hieu,
                    ten_thanh_phan: ten_tp,
                    diem: diem_tp,
                });
            }

            processed_courses.push(ProcessedCourse {
                ma_mon,
                ten_mon,
                nhom_to,
                so_tin_chi,
                diem_thi,
                diem_giua_ky,
                diem_tk,
                diem_tk_so,
                diem_chu,
                ket_qua,
                is_dat,
                components: processed_components,
            });
        }

        total_courses += processed_courses.len();

        processed_semesters.push(ProcessedSemester {
            hoc_ky,
            ten_hoc_ky,
            dtb_hk_10: val_to_str(sem.get("dtb_hk_he10")).trim().to_string(),
            dtb_hk_4: val_to_str(sem.get("dtb_hk_he4")).trim().to_string(),
            so_tin_chi_hk: val_to_str(sem.get("so_tin_chi_dat_hk")).trim().to_string(),
            diem_rl_hk: val_to_str(sem.get("diemrl_hk")).trim().to_string(),
            phan_loai_rl_hk: val_to_str(sem.get("phan_loai_rl_hk")).trim().to_string(),
            dtb_tich_luy_10: val_to_str(sem.get("dtb_tich_luy_he_10")).trim().to_string(),
            dtb_tich_luy_4: val_to_str(sem.get("dtb_tich_luy_he_4")).trim().to_string(),
            so_tin_chi_tich_luy: val_to_str(sem.get("so_tin_chi_dat_tich_luy")).trim().to_string(),
            xep_loai_tkb_hk: val_to_str(sem.get("xep_loai_tkb_hk")).trim().to_string(),
            diem_rl_tich_luy: val_to_str(sem.get("diemrl_hk_tl")).trim().to_string(),
            courses: processed_courses,
        });
    }

    // Determine cumulative overview
    let latest_cum = processed_semesters.iter().rev().find(|s| {
        !s.dtb_tich_luy_4.is_empty() && s.dtb_tich_luy_4 != "-" && s.dtb_tich_luy_4 != "0"
    }).or_else(|| processed_semesters.last());

    let (gpa4, gpa10, tc_tich_luy, xep_loai) = match latest_cum {
        Some(s) => {
            let g4 = if s.dtb_tich_luy_4 != "-" { s.dtb_tich_luy_4.clone() } else { String::new() };
            let g10 = if s.dtb_tich_luy_10 != "-" { s.dtb_tich_luy_10.clone() } else { String::new() };
            let tc = if s.so_tin_chi_tich_luy != "-" { s.so_tin_chi_tich_luy.clone() } else { String::new() };
            let xl = if !s.xep_loai_tkb_hk.is_empty() && s.xep_loai_tkb_hk != "-" {
                s.xep_loai_tkb_hk.clone()
            } else {
                classify_gpa(&g4).to_string()
            };
            (g4, g10, tc, xl)
        }
        None => (String::new(), String::new(), String::new(), String::new()),
    };

    // Pack TKB1G
    let mut out = binary_header(b'G');
    binary_str(&mut out, student_id)?;
    binary_str(&mut out, student_name)?;
    binary_i64(&mut out, updated_at);

    // Overview
    binary_str(&mut out, &gpa4)?;
    binary_str(&mut out, &gpa10)?;
    binary_str(&mut out, &tc_tich_luy)?;
    binary_str(&mut out, &xep_loai)?;
    binary_i32(&mut out, total_courses as i32);
    binary_str(&mut out, &tc_tich_luy)?;

    // Semesters
    binary_count(&mut out, processed_semesters.len())?;
    for sem in &processed_semesters {
        binary_str(&mut out, &sem.hoc_ky)?;
        binary_str(&mut out, &sem.ten_hoc_ky)?;
        binary_str(&mut out, &sem.dtb_hk_10)?;
        binary_str(&mut out, &sem.dtb_hk_4)?;
        binary_str(&mut out, &sem.so_tin_chi_hk)?;
        binary_str(&mut out, &sem.diem_rl_hk)?;
        binary_str(&mut out, &sem.phan_loai_rl_hk)?;
        binary_str(&mut out, &sem.dtb_tich_luy_10)?;
        binary_str(&mut out, &sem.dtb_tich_luy_4)?;
        binary_str(&mut out, &sem.so_tin_chi_tich_luy)?;
        binary_str(&mut out, &sem.xep_loai_tkb_hk)?;
        binary_str(&mut out, &sem.diem_rl_tich_luy)?;

        binary_count(&mut out, sem.courses.len())?;
        for course in &sem.courses {
            binary_str(&mut out, &course.ma_mon)?;
            binary_str(&mut out, &course.ten_mon)?;
            binary_str(&mut out, &course.nhom_to)?;
            binary_str(&mut out, &course.so_tin_chi)?;
            binary_str(&mut out, &course.diem_thi)?;
            binary_str(&mut out, &course.diem_giua_ky)?;
            binary_str(&mut out, &course.diem_tk)?;
            binary_str(&mut out, &course.diem_tk_so)?;
            binary_str(&mut out, &course.diem_chu)?;
            binary_str(&mut out, &course.ket_qua)?;
            out.push(u8::from(course.is_dat));

            binary_count(&mut out, course.components.len())?;
            for comp in &course.components {
                binary_str(&mut out, &comp.ky_hieu)?;
                binary_str(&mut out, &comp.ten_thanh_phan)?;
                binary_str(&mut out, &comp.diem)?;
            }
        }
    }

    // Symbols dictionary
    binary_count(&mut out, symbols.len())?;
    for (k, v) in &symbols {
        binary_str(&mut out, k)?;
        binary_str(&mut out, v)?;
    }

    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_and_packs_tnut_grades_cleanly() {
        let grades_json = r#"{
            "code": 200,
            "data": {
                "ds_diem_hocky": [
                    {
                        "hoc_ky": "20241",
                        "ten_hoc_ky": "Học kỳ 1 năm học 2024 - 2025",
                        "dtb_hk_he10": "8.5",
                        "dtb_hk_he4": "3.5",
                        "so_tin_chi_dat_hk": "18",
                        "diemrl_hk": "85",
                        "phan_loai_rl_hk": "Tốt",
                        "dtb_tich_luy_he_10": "8.5",
                        "dtb_tich_luy_he_4": "3.5",
                        "so_tin_chi_dat_tich_luy": "18",
                        "xep_loai_tkb_hk": "Giỏi",
                        "diemrl_hk_tl": "85",
                        "ds_diem_mon_hoc": [
                            {
                                "ma_mon": "MAT101",
                                "ten_mon": "Giải tích 1",
                                "nhom_to": "01",
                                "so_tin_chi": "3",
                                "diem_thi": "8.0",
                                "diem_giua_ky": "8.5",
                                "diem_tk": "8.2",
                                "diem_tk_so": "3.5",
                                "diem_tk_chu": "B+",
                                "ket_qua": "Đạt",
                                "ds_diem_thanh_phan": [
                                    { "ky_hieu": "CC", "ten_thanh_phan": "", "diem_thanh_phan": "9.0" }
                                ]
                            }
                        ]
                    }
                ]
            }
        }"#.as_bytes();

        let wire = parse_and_pack_grades("k255520216478", "Nguyễn Văn A", grades_json, b"", 1700000000000).unwrap();
        assert!(wire.starts_with(b"TKB1G"));
    }

    #[test]
    fn parses_real_world_payload_with_nulls_and_numbers() {
        let real_json = r#"{"code": 200, "data": {"ds_diem_hocky": [{"hoc_ky": "20251", "ten_hoc_ky": "Học kỳ 1", "dtb_hk_he10": 7.75, "dtb_hk_he4": 3.31, "so_tin_chi_dat_hk": 8, "diemrl_hk": 86, "phan_loai_rl_hk": "Tốt", "dtb_tich_luy_he_10": 7.75, "dtb_tich_luy_he_4": 3.31, "so_tin_chi_dat_tich_luy": 8, "xep_loai_tkb_hk": "Bình thường", "diemrl_hk_tl": 86, "ds_diem_mon_hoc": [{"ma_mon": "180000", "ten_mon": "Sinh hoạt lớp", "nhom_to": "61DKT6", "so_tin_chi": 0, "diem_thi": null, "diem_giua_ky": null, "diem_tk": null, "diem_tk_so": null, "diem_tk_chu": null, "ket_qua": "Đạt", "ds_diem_thanh_phan": []}]}]}}"#.as_bytes();
        let wire = parse_and_pack_grades("k255520216478", "Nguyễn Văn A", real_json, b"", 1700000000000).unwrap();
        assert!(wire.starts_with(b"TKB1G"));
    }

    #[test]
    fn parses_with_string_code_and_missing_wrapper() {
        let raw_json = r#"{"code": "200", "data": {"ds_diem_hocky": []}}"#.as_bytes();
        let wire = parse_and_pack_grades("k255520216478", "Nguyễn Văn A", raw_json, b"", 1700000000000).unwrap();
        assert!(wire.starts_with(b"TKB1G"));
    }

    #[test]
    fn determines_is_dat_correctly() {
        assert!(determine_is_dat("Đạt", "A", "9.0"));
        assert!(!determine_is_dat("Không đạt", "F", "3.0"));
        assert!(!determine_is_dat("", "F", "2.0"));
        assert!(determine_is_dat("", "B+", "8.0"));
        assert!(determine_is_dat("", "", "5.0"));
        assert!(!determine_is_dat("", "", "3.5"));
    }

    #[test]
    fn classifies_gpa_properly() {
        assert_eq!(classify_gpa("3.7"), "Xuất sắc");
        assert_eq!(classify_gpa("3.4"), "Giỏi");
        assert_eq!(classify_gpa("2.8"), "Khá");
        assert_eq!(classify_gpa("2.2"), "Trung bình");
        assert_eq!(classify_gpa("1.8"), "Yếu");
    }
}
