package com.habitly.app.domain

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
    val reminderTime: String? = null,
)

data class HabitTemplateGroup(val title: String, val templates: List<HabitTemplate>)

object HabitTemplates {

    val groups: List<HabitTemplateGroup> = listOf(
        HabitTemplateGroup(
            title = "Essentials",
            templates = listOf(
                HabitTemplate("Drink water", "water_drop", "#1098AD", "Health", reminderTime = "09:00"),
                HabitTemplate("Sleep by 11pm", "sleep", "#4C6EF5", "Health", reminderTime = "22:30"),
                HabitTemplate("Eat breakfast", "food", "#F6BD60", "Health", reminderTime = "08:00"),
                HabitTemplate("Take vitamins", "star", "#12B886", "Health", reminderTime = "09:00"),
            ),
        ),
        HabitTemplateGroup(
            title = "Keep active",
            templates = listOf(
                HabitTemplate("Workout", "fitness_center", "#F76C5E", "Fitness", reminderTime = "18:00"),
                HabitTemplate("Morning run", "run", "#F08C00", "Fitness", reminderTime = "06:30"),
                HabitTemplate("10k steps", "run", "#12B886", "Fitness"),
                HabitTemplate("Stretch", "self_improvement", "#6B9080", "Fitness", reminderTime = "07:00"),
            ),
        ),
        HabitTemplateGroup(
            title = "Calm the mind",
            templates = listOf(
                HabitTemplate("Meditate", "self_improvement", "#AE3EC9", "Wellness", reminderTime = "07:00"),
                HabitTemplate("Journal", "menu_book", "#495057", "Wellness", reminderTime = "21:00"),
                HabitTemplate("No screens after 10pm", "sleep", "#4C6EF5", "Wellness", reminderTime = "22:00"),
                HabitTemplate("Walk outside", "run", "#6B9080", "Wellness"),
            ),
        ),
        HabitTemplateGroup(
            title = "Learn something",
            templates = listOf(
                HabitTemplate("Read 20 minutes", "menu_book", "#F08C00", "Learning", reminderTime = "20:00"),
                HabitTemplate("Practise a language", "menu_book", "#E64980", "Learning", reminderTime = "19:00"),
                HabitTemplate("Code for an hour", "star", "#1098AD", "Learning"),
                HabitTemplate(
                    name = "Weekly review",
                    icon = "star",
                    colorHex = "#495057",
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
