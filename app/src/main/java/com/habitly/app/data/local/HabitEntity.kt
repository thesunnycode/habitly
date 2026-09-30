package com.habitly.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitly.app.domain.FrequencyType

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colorHex: String,
    val category: String,
    val frequencyType: FrequencyType,
    val frequencyDays: String?,
    val intervalDays: Int?,
    val reminderTime: String?,
    val createdAt: Long,
    val isArchived: Boolean,
    /** How many units count as a full day, or null for a plain done/not-done habit. */
    val targetCount: Int? = null,
    /** What one unit is called — "cups", "pages", "minutes". */
    val unit: String? = null,
    /** Position in the user's custom order; lower comes first. */
    val sortOrder: Int = 0,
)
