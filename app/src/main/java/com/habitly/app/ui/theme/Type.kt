package com.habitly.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitly.app.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

// Spec §3.3. Screens use these roles only.
val HabitlyTypography = Typography(
    displayLarge = style(40, 46, FontWeight.ExtraBold),
    displayMedium = style(36, 42, FontWeight.ExtraBold),
    displaySmall = style(32, 38, FontWeight.ExtraBold),          // display
    headlineLarge = style(28, 34, FontWeight.ExtraBold),
    headlineMedium = style(26, 32, FontWeight.ExtraBold),        // headline — screen titles
    headlineSmall = style(22, 26, FontWeight.ExtraBold).copy(    // stat — tile numbers
        fontFeatureSettings = "tnum",
    ),
    titleLarge = style(20, 26, FontWeight.Bold),                 // title
    titleMedium = style(16, 22, FontWeight.Bold),                // titleSmall — card titles
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(15, 22, FontWeight.Medium),                // body
    bodyMedium = style(13, 18, FontWeight.Medium),               // bodySmall — metadata
    bodySmall = style(12, 16, FontWeight.Medium),
    labelLarge = style(14, 20, FontWeight.SemiBold),             // buttons
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.6f),      // label — section labels
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.4f),
)

val HabitlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
