package com.studyapp.ui.theme

import androidx.compose.material3.Typography as Material3Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 等宽字体 — 使用系统等宽字体代替 Fira Code */
val FiraCode = FontFamily.Monospace

/** 无衬线字体 — 使用系统默认字体代替 Fira Sans */
val FiraSans = FontFamily.Default

/** 默认 Typography — 使用系统默认字体 */
val DefaultTypography = Material3Typography()

/** HUD 风格 Typography — 标题用 Fira Code，正文用 Fira Sans */
val HudTypography = Material3Typography(
    // 大标题（监控中心标题）
    titleLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 2.sp
    ),
    // 中等标题（面板标题）
    titleMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    ),
    // 数据数值（卡片中的大数字）
    displayLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 1.sp
    ),
    // 排行数值
    displaySmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    ),
    // 正文（标签、面板内容）
    bodyLarge = TextStyle(
        fontFamily = FiraSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FiraSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FiraSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    ),
    // 标签（卡片标签文字）
    labelMedium = TextStyle(
        fontFamily = FiraSans,
        fontWeight = FontWeight.Light,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp
    )
)
