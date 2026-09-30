package com.habitly.app.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {

    @Test
    fun migrationAddsSortOrderSeededFromId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        // Build a v3 schema by hand: the columns HabitEntity had before this change.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "name TEXT NOT NULL, icon TEXT NOT NULL, colorHex TEXT NOT NULL, " +
                                "category TEXT NOT NULL, frequencyType TEXT NOT NULL, frequencyDays TEXT, " +
                                "intervalDays INTEGER, reminderTime TEXT, createdAt INTEGER NOT NULL, " +
                                "isArchived INTEGER NOT NULL, targetCount INTEGER, unit TEXT)"
                        )
                        db.execSQL(
                            "CREATE TABLE habit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "habitId INTEGER NOT NULL, date TEXT NOT NULL, status TEXT NOT NULL, " +
                                "completedAt INTEGER, note TEXT, progress INTEGER NOT NULL DEFAULT 0, " +
                                "FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                        )
                        db.execSQL("CREATE INDEX index_habit_logs_habitId ON habit_logs (habitId)")
                        db.execSQL("CREATE UNIQUE INDEX index_habit_logs_habitId_date ON habit_logs (habitId, date)")
                        db.execSQL(
                            "CREATE TABLE achievements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "habitId INTEGER, type TEXT NOT NULL, unlockedAt INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO habits (id,name,icon,colorHex,category,frequencyType,createdAt,isArchived) " +
                                "VALUES (7,'Read','menu_book','#FF922B','Mind','DAILY',1000,0)"
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) = Unit
                }).build()
        )
        helper.writableDatabase.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val habit = db.habitDao().observeActiveHabits().first().single()
        assertEquals(7, habit.sortOrder)
        db.close()
        context.deleteDatabase(name)
        Unit
    }
}
