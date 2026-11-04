package com.watermarker.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * توکن‌ها از تم پیش‌فرض Amplify UI (https://ui.docs.amplify.aws/react/theming/default-theme)
 * گرفته شده‌اند. مقدارهای HSL سایت به hex تبدیل شده‌اند.
 */

// برند اصلی (سبزآبی)
private val Primary10 = Color(0xFFE9F9FC)
private val Primary20 = Color(0xFFBCECF5)
private val Primary40 = Color(0xFF7DD6E8)
private val Primary80 = Color(0xFF047D95)
private val Primary90 = Color(0xFF005566)
private val Primary100 = Color(0xFF00404C)

// برند ثانویه (بنفش)
private val Secondary20 = Color(0xFFF9B8F9)
private val Secondary40 = Color(0xFFE87DE8)
private val Secondary80 = Color(0xFF950495)
private val Secondary90 = Color(0xFF660066)
private val Secondary100 = Color(0xFF4C004C)
private val Secondary10 = Color(0xFFFEE6FE)

// خنثی (خاکستری مایل به آبی)
private val Neutral10 = Color(0xFFFAFAFA)
private val Neutral20 = Color(0xFFEFF0F0)
private val Neutral40 = Color(0xFFDCDEE0)
private val Neutral60 = Color(0xFF89949F)
private val Neutral80 = Color(0xFF5C6670)
private val Neutral90 = Color(0xFF304050)
private val Neutral100 = Color(0xFF0D1926)

private val LightScheme = lightColorScheme(
    primary = Primary80,
    onPrimary = Color.White,
    primaryContainer = Primary20,
    onPrimaryContainer = Primary100,
    secondary = Secondary80,
    onSecondary = Color.White,
    secondaryContainer = Secondary10,
    onSecondaryContainer = Secondary100,
    background = Color.White,
    onBackground = Neutral100,
    surface = Color.White,
    onSurface = Neutral100,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral80,
    surfaceTint = Primary80,
    outline = Neutral60,
    outlineVariant = Neutral40,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral10,
    inversePrimary = Primary40,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral20,
    surfaceContainerHigh = Neutral20,
    surfaceContainerHighest = Neutral40,
)

// Amplify رنگ‌های حالت تاریک را در این صفحه نداده؛ این‌ها از همان پالت مشتق شده‌اند
private val DarkScheme = darkColorScheme(
    primary = Primary40,
    onPrimary = Primary100,
    primaryContainer = Primary90,
    onPrimaryContainer = Primary20,
    secondary = Secondary40,
    onSecondary = Secondary100,
    secondaryContainer = Secondary90,
    onSecondaryContainer = Secondary20,
    background = Neutral100,
    onBackground = Neutral10,
    surface = Neutral100,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral40,
    surfaceTint = Primary40,
    outline = Neutral60,
    outlineVariant = Neutral80,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral90,
    inversePrimary = Primary80,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral100,
    surfaceContainer = Neutral90,
    surfaceContainerHigh = Neutral90,
    surfaceContainerHighest = Neutral80,
)

// radii: xs 2، small 4، medium 8، large 16، xl 32
private val AmplifyShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

// اندازه‌ها: small 14، medium 16، large 20، xl 24. ارتفاع خط ۱٫۵ برابر
private val BaseTypography = Typography()
private val AmplifyTypography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(
        fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    bodyMedium = BaseTypography.bodyMedium.copy(
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp
    ),
    bodySmall = BaseTypography.bodySmall.copy(
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp
    ),
    labelLarge = BaseTypography.labelLarge.copy(
        fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp
    ),
)

@Composable
fun WatermarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AmplifyTypography,
        shapes = AmplifyShapes,
        content = content,
    )
}
