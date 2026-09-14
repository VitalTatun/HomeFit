package com.example.homefit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.homefit.R

// Google Sans (static cuts: Regular 400, Medium 500, Bold 700).
// Material 3 default type scale uses only Regular + Medium; Bold is added for emphasis.
val GoogleSans = FontFamily(
    Font(R.font.googlesans_regular, FontWeight.Normal),
    Font(R.font.googlesans_medium, FontWeight.Medium),
    Font(R.font.googlesans_bold, FontWeight.Bold)
)

// Material 3 default type scale with only the font family replaced by Google Sans.
// All sizes, line heights, letter spacing and weights are preserved.
private val DefaultTypography = Typography()

val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = GoogleSans),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = GoogleSans),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = GoogleSans),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = GoogleSans),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = GoogleSans),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = GoogleSans),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = GoogleSans),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = GoogleSans),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = GoogleSans),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = GoogleSans),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = GoogleSans),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = GoogleSans),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = GoogleSans),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = GoogleSans),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = GoogleSans)
)
