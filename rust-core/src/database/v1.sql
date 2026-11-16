CREATE TABLE IF NOT EXISTS `periods` (
    `tiet` INTEGER NOT NULL,
    `gioBatDau` TEXT NOT NULL,
    `gioKetThuc` TEXT NOT NULL,
    PRIMARY KEY (`tiet`)
);
CREATE TABLE IF NOT EXISTS `weeks` (
    `hocKy` INTEGER NOT NULL,
    `tuanHocKy` INTEGER NOT NULL,
    `ngayBatDau` TEXT NOT NULL,
    `ngayKetThuc` TEXT NOT NULL,
    `thongTinTuan` TEXT NOT NULL,
    PRIMARY KEY (`hocKy`, `tuanHocKy`)
);
CREATE TABLE IF NOT EXISTS `lessons` (
    `idTkb` TEXT NOT NULL,
    `hocKy` INTEGER NOT NULL,
    `tuanHocKy` INTEGER NOT NULL,
    `ngayHoc` TEXT NOT NULL,
    `thuKieuSo` INTEGER NOT NULL,
    `tietBatDau` INTEGER NOT NULL,
    `soTiet` INTEGER NOT NULL,
    `maMon` TEXT NOT NULL,
    `tenMon` TEXT NOT NULL,
    `soTinChi` TEXT NOT NULL,
    `maPhong` TEXT NOT NULL,
    `tenGiangVien` TEXT NOT NULL,
    `tenLop` TEXT NOT NULL,
    `maNhom` TEXT NOT NULL,
    PRIMARY KEY (`idTkb`)
);
CREATE INDEX IF NOT EXISTS index_lessons_hocKy_tuanHocKy ON lessons(hocKy, tuanHocKy);
CREATE INDEX IF NOT EXISTS index_lessons_ngayHoc ON lessons(ngayHoc);
