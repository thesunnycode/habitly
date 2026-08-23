package com.habitly.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.habitly.app.domain.CompletionStatus

@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("habitId"), Index(value = ["habitId", "date"], unique = true)],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String,
    val status: CompletionStatus,
    val completedAt: Long?,
    val note: String?,
    /** Units logged on this day, for habits that carry a numeric goal. */
    val progress: Int = 0,
)
