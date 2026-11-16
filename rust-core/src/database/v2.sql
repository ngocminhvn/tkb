CREATE TABLE IF NOT EXISTS `exams` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `hocKy` INTEGER NOT NULL,
    `isGiuaKy` INTEGER NOT NULL,
    `soThuTu` INTEGER NOT NULL,
    `maMon` TEXT NOT NULL,
    `tenMon` TEXT NOT NULL,
    `ngayThi` TEXT NOT NULL,
    `gioBatDau` TEXT NOT NULL,
    `soPhut` INTEGER NOT NULL,
    `maPhong` TEXT NOT NULL,
    `diaDiemThi` TEXT NOT NULL,
    `hinhThucThi` TEXT NOT NULL,
    `soBaoDanh` TEXT NOT NULL,
    `dotThi` TEXT NOT NULL,
    `ghiChuSv` TEXT NOT NULL,
    `isHoanThi` INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_exams_hocKy_ngayThi ON exams(hocKy, ngayThi);
