package com.habitly.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType

class Converters {
    @TypeConverter
    fun fromFrequencyType(value: FrequencyType): String = value.name

    @TypeConverter
    fun toFrequencyType(value: String): FrequencyType = FrequencyType.valueOf(value)

    @TypeConverter
    fun fromCompletionStatus(value: CompletionStatus): String = value.name

    @TypeConverter
    fun toCompletionStatus(value: String): CompletionStatus = CompletionStatus.valueOf(value)
}

/**
 * habit_logs.completed (boolean) becomes habit_logs.status, so a day can be a declared rest day
 * rather than only done-or-not. Existing completions map to DONE and everything else to MISSED,
 * which is what those rows already meant.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `habit_logs_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `habitId` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `completedAt` INTEGER,
                `note` TEXT,
                FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `habit_logs_new` (`id`, `habitId`, `date`, `status`, `completedAt`, `note`)
            SELECT `id`, `habitId`, `date`,
                   CASE WHEN `completed` = 1 THEN 'DONE' ELSE 'MISSED' END,
                   `completedAt`, `note`
            FROM `habit_logs`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `habit_logs`")
        db.execSQL("ALTER TABLE `habit_logs_new` RENAME TO `habit_logs`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_habitId` ON `habit_logs` (`habitId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_logs_habitId_date` " +
                "ON `habit_logs` (`habitId`, `date`)"
        )
    }
}

/**
 * Habits gain a numeric goal (target plus unit) and logs gain the units recorded that day, so a
 * habit can be "2 of 3 cups" rather than only done or not.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `targetCount` INTEGER")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `unit` TEXT")
        db.execSQL("ALTER TABLE `habit_logs` ADD COLUMN `progress` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Habits gain a user-controlled order; existing habits keep their creation order. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `habits` SET `sortOrder` = `id`")
    }
}

@Database(
    entities = [HabitEntity::class, HabitLogEntity::class, AchievementEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "habitly.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
