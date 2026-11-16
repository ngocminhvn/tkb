//! Typed rows retain the exact Room v4 column names and SQLite affinities.
use rusqlite::types::Value;
use rusqlite::{params_from_iter, Connection, Result, Row};

pub(super) trait Record: Sized {
    const TABLE: &'static str;
    const COLUMNS: &'static str;
    fn from_row(row: &Row<'_>) -> Result<Self>;
    fn values(&self) -> Vec<Value>;
}

pub(super) fn insert<R: Record>(db: &Connection, rows: &[R]) -> Result<()> {
    let placeholders = (0..R::COLUMNS.split(',').count())
        .map(|_| "?")
        .collect::<Vec<_>>()
        .join(",");
    let sql = format!(
        "INSERT OR REPLACE INTO {} ({}) VALUES ({})",
        R::TABLE,
        R::COLUMNS,
        placeholders
    );
    let mut statement = db.prepare_cached(&sql)?;
    for row in rows {
        statement.execute(params_from_iter(row.values()))?;
    }
    Ok(())
}

pub(super) fn query<R: Record>(
    db: &Connection,
    suffix: &str,
    values: impl rusqlite::Params,
) -> Result<Vec<R>> {
    let sql = format!("SELECT {} FROM {} {}", R::COLUMNS, R::TABLE, suffix);
    let mut statement = db.prepare_cached(&sql)?;
    let rows = statement.query_map(values, R::from_row)?.collect();
    rows
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Period {
    pub tiet: i32,
    pub gio_bat_dau: String,
    pub gio_ket_thuc: String,
}

impl Record for Period {
    const TABLE: &'static str = "periods";
    const COLUMNS: &'static str = "tiet,gioBatDau,gioKetThuc";
    fn from_row(row: &Row<'_>) -> Result<Self> {
        Ok(Self {
            tiet: row.get(0)?,
            gio_bat_dau: row.get(1)?,
            gio_ket_thuc: row.get(2)?,
        })
    }
    fn values(&self) -> Vec<Value> {
        vec![
            Value::Integer(i64::from(self.tiet)),
            Value::Text(self.gio_bat_dau.clone()),
            Value::Text(self.gio_ket_thuc.clone()),
        ]
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Week {
    pub hoc_ky: i32,
    pub tuan_hoc_ky: i32,
    pub ngay_bat_dau: String,
    pub ngay_ket_thuc: String,
    pub thong_tin_tuan: String,
}

impl Record for Week {
    const TABLE: &'static str = "weeks";
    const COLUMNS: &'static str = "hocKy,tuanHocKy,ngayBatDau,ngayKetThuc,thongTinTuan";
    fn from_row(row: &Row<'_>) -> Result<Self> {
        Ok(Self {
            hoc_ky: row.get(0)?,
            tuan_hoc_ky: row.get(1)?,
            ngay_bat_dau: row.get(2)?,
            ngay_ket_thuc: row.get(3)?,
            thong_tin_tuan: row.get(4)?,
        })
    }
    fn values(&self) -> Vec<Value> {
        vec![
            Value::Integer(i64::from(self.hoc_ky)),
            Value::Integer(i64::from(self.tuan_hoc_ky)),
            Value::Text(self.ngay_bat_dau.clone()),
            Value::Text(self.ngay_ket_thuc.clone()),
            Value::Text(self.thong_tin_tuan.clone()),
        ]
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Lesson {
    pub id_tkb: String,
    pub hoc_ky: i32,
    pub tuan_hoc_ky: i32,
    pub ngay_hoc: String,
    pub thu_kieu_so: i32,
    pub tiet_bat_dau: i32,
    pub so_tiet: i32,
    pub ma_mon: String,
    pub ten_mon: String,
    pub so_tin_chi: String,
    pub ma_phong: String,
    pub ten_giang_vien: String,
    pub ten_lop: String,
    pub ma_nhom: String,
}

impl Record for Lesson {
    const TABLE: &'static str = "lessons";
    const COLUMNS: &'static str = "idTkb,hocKy,tuanHocKy,ngayHoc,thuKieuSo,tietBatDau,soTiet,maMon,tenMon,soTinChi,maPhong,tenGiangVien,tenLop,maNhom";
    fn from_row(row: &Row<'_>) -> Result<Self> {
        Ok(Self {
            id_tkb: row.get(0)?,
            hoc_ky: row.get(1)?,
            tuan_hoc_ky: row.get(2)?,
            ngay_hoc: row.get(3)?,
            thu_kieu_so: row.get(4)?,
            tiet_bat_dau: row.get(5)?,
            so_tiet: row.get(6)?,
            ma_mon: row.get(7)?,
            ten_mon: row.get(8)?,
            so_tin_chi: row.get(9)?,
            ma_phong: row.get(10)?,
            ten_giang_vien: row.get(11)?,
            ten_lop: row.get(12)?,
            ma_nhom: row.get(13)?,
        })
    }
    fn values(&self) -> Vec<Value> {
        vec![
            Value::Text(self.id_tkb.clone()),
            Value::Integer(i64::from(self.hoc_ky)),
            Value::Integer(i64::from(self.tuan_hoc_ky)),
            Value::Text(self.ngay_hoc.clone()),
            Value::Integer(i64::from(self.thu_kieu_so)),
            Value::Integer(i64::from(self.tiet_bat_dau)),
            Value::Integer(i64::from(self.so_tiet)),
            Value::Text(self.ma_mon.clone()),
            Value::Text(self.ten_mon.clone()),
            Value::Text(self.so_tin_chi.clone()),
            Value::Text(self.ma_phong.clone()),
            Value::Text(self.ten_giang_vien.clone()),
            Value::Text(self.ten_lop.clone()),
            Value::Text(self.ma_nhom.clone()),
        ]
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LessonNote {
    pub lesson_id: String,
    pub note: String,
    pub updated_at: i64,
}

impl Record for LessonNote {
    const TABLE: &'static str = "lesson_notes";
    const COLUMNS: &'static str = "lessonId,note,updatedAt";
    fn from_row(row: &Row<'_>) -> Result<Self> {
        Ok(Self {
            lesson_id: row.get(0)?,
            note: row.get(1)?,
            updated_at: row.get(2)?,
        })
    }
    fn values(&self) -> Vec<Value> {
        vec![
            Value::Text(self.lesson_id.clone()),
            Value::Text(self.note.clone()),
            Value::Integer(self.updated_at),
        ]
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Exam {
    pub id: i64,
    pub hoc_ky: i32,
    pub is_giua_ky: bool,
    pub so_thu_tu: i32,
    pub ma_mon: String,
    pub ten_mon: String,
    pub ngay_thi: String,
    pub gio_bat_dau: String,
    pub so_phut: i32,
    pub ma_phong: String,
    pub dia_diem_thi: String,
    pub hinh_thuc_thi: String,
    pub so_bao_danh: String,
    pub dot_thi: String,
    pub ghi_chu_sv: String,
    pub is_hoan_thi: bool,
}

impl Record for Exam {
    const TABLE: &'static str = "exams";
    const COLUMNS: &'static str = "id,hocKy,isGiuaKy,soThuTu,maMon,tenMon,ngayThi,gioBatDau,soPhut,maPhong,diaDiemThi,hinhThucThi,soBaoDanh,dotThi,ghiChuSv,isHoanThi";
    fn from_row(row: &Row<'_>) -> Result<Self> {
        Ok(Self {
            id: row.get(0)?,
            hoc_ky: row.get(1)?,
            is_giua_ky: row.get(2)?,
            so_thu_tu: row.get(3)?,
            ma_mon: row.get(4)?,
            ten_mon: row.get(5)?,
            ngay_thi: row.get(6)?,
            gio_bat_dau: row.get(7)?,
            so_phut: row.get(8)?,
            ma_phong: row.get(9)?,
            dia_diem_thi: row.get(10)?,
            hinh_thuc_thi: row.get(11)?,
            so_bao_danh: row.get(12)?,
            dot_thi: row.get(13)?,
            ghi_chu_sv: row.get(14)?,
            is_hoan_thi: row.get(15)?,
        })
    }
    fn values(&self) -> Vec<Value> {
        vec![
            if self.id == 0 {
                Value::Null
            } else {
                Value::Integer(self.id)
            },
            Value::Integer(i64::from(self.hoc_ky)),
            Value::Integer(i64::from(self.is_giua_ky)),
            Value::Integer(i64::from(self.so_thu_tu)),
            Value::Text(self.ma_mon.clone()),
            Value::Text(self.ten_mon.clone()),
            Value::Text(self.ngay_thi.clone()),
            Value::Text(self.gio_bat_dau.clone()),
            Value::Integer(i64::from(self.so_phut)),
            Value::Text(self.ma_phong.clone()),
            Value::Text(self.dia_diem_thi.clone()),
            Value::Text(self.hinh_thuc_thi.clone()),
            Value::Text(self.so_bao_danh.clone()),
            Value::Text(self.dot_thi.clone()),
            Value::Text(self.ghi_chu_sv.clone()),
            Value::Integer(i64::from(self.is_hoan_thi)),
        ]
    }
}
