package com.sv.tnu.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey val tiet: Int,
    val gioBatDau: String,
    val gioKetThuc: String,
)

@Entity(tableName = "weeks", primaryKeys = ["hocKy", "tuanHocKy"])
data class WeekEntity(
    val hocKy: Int,
    val tuanHocKy: Int,
    val ngayBatDau: String,
    val ngayKetThuc: String,
    val thongTinTuan: String,
)

@Entity(
    tableName = "lessons",
    indices = [
        Index(value = ["hocKy", "tuanHocKy"]),
        Index(value = ["ngayHoc"]),
    ],
)
data class LessonEntity(
    @PrimaryKey val idTkb: String,
    val hocKy: Int,
    val tuanHocKy: Int,
    val ngayHoc: String,
    val thuKieuSo: Int,
    val tietBatDau: Int,
    val soTiet: Int,
    val maMon: String,
    val tenMon: String,
    val soTinChi: String,
    val maPhong: String,
    val tenGiangVien: String,
    val tenLop: String,
    val maNhom: String,
)

@Entity(tableName = "lesson_notes")
data class LessonNoteEntity(
    @PrimaryKey val lessonId: String,
    val note: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "exams",
    indices = [
        Index(value = ["hocKy", "ngayThi"]),
        Index(value = ["ngayThi"]),
    ],
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hocKy: Int,
    val isGiuaKy: Boolean = false,
    val soThuTu: Int = 0,
    val maMon: String = "",
    val tenMon: String = "",
    val ngayThi: String = "", // yyyy-MM-dd
    val gioBatDau: String = "", // HH:mm
    val soPhut: Int = 0,
    val maPhong: String = "",
    val diaDiemThi: String = "",
    val hinhThucThi: String = "",
    val soBaoDanh: String = "",
    val dotThi: String = "",
    val ghiChuSv: String = "",
    val isHoanThi: Boolean = false,
)

@Dao
interface TkbDao {
    @Query("SELECT * FROM periods ORDER BY tiet ASC")
    fun periods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods ORDER BY tiet ASC")
    suspend fun periodsOnce(): List<PeriodEntity>

    @Query("SELECT * FROM weeks WHERE hocKy = :hocKy ORDER BY tuanHocKy ASC")
    fun weeks(hocKy: Int): Flow<List<WeekEntity>>

    @Query("SELECT * FROM lessons WHERE hocKy = :hocKy AND tuanHocKy = :tuan ORDER BY ngayHoc ASC, tietBatDau ASC")
    fun lessons(hocKy: Int, tuan: Int): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE hocKy = :hocKy ORDER BY ngayHoc ASC")
    fun semesterLessons(hocKy: Int): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE ngayHoc LIKE :date || '%' ORDER BY tietBatDau ASC")
    suspend fun lessonsOn(date: String): List<LessonEntity>

    @Query("SELECT * FROM exams WHERE ngayThi LIKE :date || '%' AND isHoanThi = 0 ORDER BY gioBatDau ASC")
    suspend fun examsOn(date: String): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE hocKy = :hocKy ORDER BY ngayThi ASC, gioBatDau ASC")
    fun exams(hocKy: Int): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE hocKy = :hocKy")
    suspend fun examsOnce(hocKy: Int): List<ExamEntity>

    @Query("SELECT * FROM lesson_notes ORDER BY updatedAt DESC")
    fun lessonNotes(): Flow<List<LessonNoteEntity>>

    @Query("SELECT COUNT(*) FROM weeks WHERE hocKy = :hocKy")
    suspend fun weekCount(hocKy: Int): Int

    @Query("SELECT COUNT(*) FROM exams WHERE hocKy = :hocKy")
    suspend fun examCount(hocKy: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriods(list: List<PeriodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeks(list: List<WeekEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(list: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(list: List<ExamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonNote(note: LessonNoteEntity)

    @Query("DELETE FROM lesson_notes WHERE lessonId = :lessonId")
    suspend fun deleteLessonNote(lessonId: String)

    @Query("DELETE FROM lesson_notes")
    suspend fun clearAllLessonNotes()

    @Query("DELETE FROM lesson_notes WHERE updatedAt < :cutoffTimestamp")
    suspend fun clearOldLessonNotes(cutoffTimestamp: Long)

    @Query("DELETE FROM lessons WHERE hocKy = :hocKy")
    suspend fun deleteLessons(hocKy: Int)

    @Query("DELETE FROM exams WHERE hocKy = :hocKy")
    suspend fun deleteExams(hocKy: Int)

    @Query("DELETE FROM exams WHERE hocKy = :hocKy AND isGiuaKy = :isGiuaKy")
    suspend fun deleteExamsByType(hocKy: Int, isGiuaKy: Boolean)

    @Query("DELETE FROM weeks WHERE hocKy = :hocKy")
    suspend fun deleteWeeks(hocKy: Int)

    @Query("DELETE FROM periods")
    suspend fun deletePeriods()

    @Transaction
    suspend fun replaceTimetable(
        hocKy: Int,
        periods: List<PeriodEntity>,
        weeks: List<WeekEntity>,
        lessons: List<LessonEntity>,
    ) {
        deleteLessons(hocKy)
        deleteWeeks(hocKy)
        deletePeriods()
        insertPeriods(periods)
        insertWeeks(weeks)
        insertLessons(lessons)
    }

    @Transaction
    suspend fun replaceExams(hocKy: Int, exams: List<ExamEntity>) {
        deleteExams(hocKy)
        insertExams(exams)
    }

    // Each TNUT exam endpoint can fail independently. Never erase the other type.
    @Transaction
    suspend fun replaceExamsByType(hocKy: Int, isGiuaKy: Boolean, exams: List<ExamEntity>) {
        deleteExamsByType(hocKy, isGiuaKy)
        insertExams(exams)
    }

    /**
     * Older APKs inserted SIX hard-coded demonstration exam rows into the
     * user's Room DB. Remove ONLY the complete, exact legacy fixture; if even
     * one row differs, keep everything to avoid deleting real portal data.
     * This is deliberately not a destructive database migration.
     */
    @Transaction
    suspend fun removeLegacySampleExamsIfExact(hocKy: Int): Boolean {
        val exams = examsOnce(hocKy)
        val fixture = setOf(
            "B103BC1|2026-11-08|14:25|THECHAT|002",
            "MEC0347|2026-11-12|07:00|DOT1|MEC0347_61TDH8_02",
            "BAS203|2026-11-15|07:00|DOT1|BAS203_61TDH8_01",
            "TEE0220|2026-11-18|12:45|DOT1|TEE0220_61TDH8_02",
            "ELE309|2026-11-25|10:45|DOT1|ELE309_61TDH8_02",
            "ELE201|2026-11-30|10:45|DOT1|ELE201_61TDH8_02",
        )
        if (exams.any { it.isGiuaKy || it.isHoanThi }) return false
        val keys = exams.map { exam ->
            "${exam.maMon}|${exam.ngayThi}|${exam.gioBatDau}|${exam.dotThi}|${exam.soBaoDanh}"
        }.toSet()
        if (exams.size != fixture.size || keys != fixture) return false
        deleteExams(hocKy)
        return true
    }
}

@Database(
    entities = [
        PeriodEntity::class,
        WeekEntity::class,
        LessonEntity::class,
        LessonNoteEntity::class,
        ExamEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tkbDao(): TkbDao

    companion object {
        // Historical v1 APKs contained periods, weeks and lessons only.
        // Preserve their cached timetable when upgrading directly to v4.
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `exams` (
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
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_hocKy_ngayThi` ON `exams` (`hocKy`, `ngayThi`)")
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `lesson_notes` (
                        `lessonId` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`lessonId`)
                    )""".trimIndent(),
                )
            }
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_ngayHoc` ON `lessons` (`ngayHoc`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_ngayThi` ON `exams` (`ngayThi`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tkb.db",
                )
                    .addMigrations(migration1To2, migration2To3, migration3To4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
