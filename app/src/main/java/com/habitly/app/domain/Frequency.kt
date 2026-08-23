package com.habitly.app.domain

import java.time.LocalDate

enum class FrequencyType { DAILY, SPECIFIC_DAYS, INTERVAL }

/**
 * What happened on one day.
 *
 * [SKIPPED] is the important one: a rest day the user declared, which leaves the streak and the
 * completion rate untouched. [MISSED] is an explicit admission that the day was lost. [PARTIAL] is
 * progress against a numeric goal that has not reached it yet. A day with no record at all is none
 * of these — it has simply not been judged yet, which is why an unlogged today does not break a
 * streak.
 */
enum class CompletionStatus {
    DONE,
    SKIPPED,
    MISSED,

    /**
     * Some of a numeric goal was logged but not all of it. Treated like an unjudged day while the
     * day is still running, and like a miss once it has passed.
     */
    PARTIAL,
}

data class CompletionRecord(val date: LocalDate, val status: CompletionStatus) {

    /** Convenience for the many places that only care whether the day was kept. */
    constructor(date: LocalDate, completed: Boolean) :
        this(date, if (completed) CompletionStatus.DONE else CompletionStatus.MISSED)

    val completed: Boolean get() = status == CompletionStatus.DONE
}
