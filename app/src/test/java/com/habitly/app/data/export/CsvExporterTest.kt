package com.habitly.app.data.export

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExporterTest {
    private fun habit(id: Long, name: String, unit: String? = null) = HabitEntity(id = id, name = name, icon = "star",
        colorHex = "#FF922B", category = "Mind", frequencyType = FrequencyType.DAILY, frequencyDays = null,
        intervalDays = null, reminderTime = null, createdAt = 0, isArchived = false, unit = unit)

    @Test fun writesAHeaderAndOneRowPerLog() {
        val csv = CsvExporter.export(listOf(habit(1, "Read")),
            listOf(HabitLogEntity(0, 1, "2026-09-28", CompletionStatus.DONE, 1L, null, 0)))
        assertEquals("habit,category,date,status,progress,unit,note\nRead,Mind,2026-09-28,DONE,0,,\n", csv)
    }

    @Test fun quotesValuesWithCommasQuotesAndNewlines() {
        val csv = CsvExporter.export(listOf(habit(1, "Read, slowly")),
            listOf(HabitLogEntity(0, 1, "2026-09-28", CompletionStatus.DONE, 1L, "said \"hi\"\nthen", 0)))
        assertTrue(csv.contains("\"Read, slowly\""))
        assertTrue(csv.contains("\"said \"\"hi\"\"\nthen\""))
    }

    @Test fun sortsByHabitThenDate() {
        val csv = CsvExporter.export(listOf(habit(1, "B"), habit(2, "A")), listOf(
            HabitLogEntity(0, 1, "2026-09-02", CompletionStatus.DONE, 1L, null, 0),
            HabitLogEntity(0, 2, "2026-09-03", CompletionStatus.DONE, 1L, null, 0),
            HabitLogEntity(0, 2, "2026-09-01", CompletionStatus.DONE, 1L, null, 0)))
        val dates = csv.lines().drop(1).filter { it.isNotBlank() }.map { it.split(",")[0] + it.split(",")[2] }
        assertEquals(listOf("A2026-09-01", "A2026-09-03", "B2026-09-02"), dates)
    }

    @Test fun fileNameIncludesTheDate() {
        assertEquals("habitly-export-2026-09-29.csv", CsvExporter.fileName(LocalDate.of(2026, 9, 29)))
    }
}
