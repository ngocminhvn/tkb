package vn.tnut.tkb.data

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
    indices = [Index(value = ["hocKy", "tuanHocKy"])],
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

    @Query("SELECT COUNT(*) FROM weeks WHERE hocKy = :hocKy")
    suspend fun weekCount(hocKy: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriods(list: List<PeriodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeks(list: List<WeekEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(list: List<LessonEntity>)

    @Query("DELETE FROM lessons WHERE hocKy = :hocKy")
    suspend fun deleteLessons(hocKy: Int)

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
}

@Database(
    entities = [PeriodEntity::class, WeekEntity::class, LessonEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tkbDao(): TkbDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tkb.db",
                ).build().also { instance = it }
            }
        }
    }
}
