package com.habitly.app.domain

import com.habitly.app.data.local.HabitEntity

/**
 * A ready-made habit. The whole value of a template is that one tap fills the name, the icon, the
 * colour, the category *and* the schedule — a gallery that only fills the name saves nothing. The
 * groups mirror the "explore new lifestyle" shelves the reference apps use.
 */
data class HabitTemplate(
    val name: String,
    val icon: String,
    val colorHex: String,
    val category: String,
    val frequencyType: FrequencyType = FrequencyType.DAILY,
    val specificDays: Set<java.time.DayOfWeek> = emptySet(),
    /** Only worth setting where counting up in single taps is realistic. */
    val targetCount: Int? = null,
    val unit: String? = null,
    val reminderTime: String? = null,
) {
    /** Stable navigation key: lower-cased name, each run of non-alphanumerics collapsed to one underscore. */
    val key: String
        get() = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

/**
 * The habit this template describes, ready to insert. `sortOrder` stays 0 so the repository places it
 * after the existing habits.
 */
fun HabitTemplate.toEntity(createdAt: Long): HabitEntity = HabitEntity(
    name = name,
    icon = icon,
    colorHex = colorHex,
    category = category,
    frequencyType = frequencyType,
    frequencyDays = specificDays
        .takeIf { frequencyType == FrequencyType.SPECIFIC_DAYS && it.isNotEmpty() }
        ?.sorted()?.joinToString(",") { it.name },
    intervalDays = null,
    reminderTime = reminderTime,
    createdAt = createdAt,
    isArchived = false,
    targetCount = targetCount?.takeIf { it > 1 },
    unit = unit,
)

data class HabitTemplateGroup(val title: String, val templates: List<HabitTemplate>)

object HabitTemplates {

    fun byKey(key: String): HabitTemplate? =
        groups.asSequence().flatMap { it.templates.asSequence() }.firstOrNull { it.key == key }

    val groups: List<HabitTemplateGroup> = listOf(
        HabitTemplateGroup(
            title = "Essentials",
            templates = listOf(
                HabitTemplate(
                    name = "Drink water",
                    icon = "water_drop",
                    colorHex = "#22B8CF",
                    category = "Health",
                    targetCount = 8,
                    unit = "cups",
                    reminderTime = "09:00",
                ),
                HabitTemplate("Sleep by 11pm", "sleep", "#748FFC", "Health", reminderTime = "22:30"),
                HabitTemplate("Eat breakfast", "food", "#FCC419", "Health", reminderTime = "08:00"),
                HabitTemplate("Take vitamins", "star", "#51CF66", "Health", reminderTime = "09:00"),
            ),
        ),
        HabitTemplateGroup(
            title = "Keep active",
            templates = listOf(
                HabitTemplate("Workout", "fitness_center", "#FF6B6B", "Fitness", reminderTime = "18:00"),
                HabitTemplate("Morning run", "run", "#FF922B", "Fitness", reminderTime = "06:30"),
                HabitTemplate("10k steps", "run", "#51CF66", "Fitness"),
                HabitTemplate("Stretch", "self_improvement", "#51CF66", "Fitness", reminderTime = "07:00"),
            ),
        ),
        HabitTemplateGroup(
            title = "Calm the mind",
            templates = listOf(
                HabitTemplate("Meditate", "self_improvement", "#9775FA", "Wellness", reminderTime = "07:00"),
                HabitTemplate("Journal", "menu_book", "#CED4DA", "Wellness", reminderTime = "21:00"),
                HabitTemplate("No screens after 10pm", "sleep", "#748FFC", "Wellness", reminderTime = "22:00"),
                HabitTemplate("Walk outside", "run", "#51CF66", "Wellness"),
            ),
        ),
        HabitTemplateGroup(
            title = "Learn something",
            templates = listOf(
                HabitTemplate("Read 20 minutes", "menu_book", "#FF922B", "Learning", reminderTime = "20:00"),
                HabitTemplate("Practise a language", "menu_book", "#F06595", "Learning", reminderTime = "19:00"),
                HabitTemplate("Code for an hour", "star", "#22B8CF", "Learning"),
                HabitTemplate(
                    name = "Weekly review",
                    icon = "star",
                    colorHex = "#CED4DA",
                    category = "Learning",
                    frequencyType = FrequencyType.SPECIFIC_DAYS,
                    specificDays = setOf(java.time.DayOfWeek.SUNDAY),
                    reminderTime = "18:00",
                ),
            ),
        ),
    )

    val all: List<HabitTemplate> get() = groups.flatMap { it.templates }
}
