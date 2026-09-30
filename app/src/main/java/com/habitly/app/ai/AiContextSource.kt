package com.habitly.app.ai

import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class AiContextSource(
    private val data: HabitDataSource,
    private val risks: suspend () -> Map<Long, Double> = { emptyMap() },
) {
    suspend fun summaries(asOf: LocalDate): List<HabitSummaryDto> =
        AiContextBuilder.build(data.observeAllHabits().first(), data.observeAllLogs().first(), asOf, risks())
}
